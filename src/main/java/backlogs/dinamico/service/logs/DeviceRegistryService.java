package backlogs.dinamico.service.logs;

import backlogs.dinamico.model.log.Device;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceRegistryService {

    private final MongoTemplate mongoTemplate;

    private static final long OFFLINE_THRESHOLD_MINUTES = 10;

    /**
     * Registra o actualiza un dispositivo cuando llega un log.
     *
     * Clave de deduplicación: hostname (identifica la máquina física).
     * Si el mismo servidor reporta con IP privada y con IP pública, ambas se
     * almacenan en {@code ipInterfaces} y se consolida en UN solo documento.
     */
    public void upsertFromLog(ObjectId tenantId, String deviceId, String system,
                              String type, String ip, String hostname,
                              Double latitude, Double longitude,
                              String locationName, String eventType) {

        if (tenantId == null || deviceId == null || deviceId.isBlank()) return;

        Query query = new Query(new Criteria().andOperator(
                Criteria.where("tenantId").is(tenantId),
                Criteria.where("deviceId").is(deviceId)
        ));

        Update update = new Update()
                .set("tenantId", tenantId)
                .set("deviceId", deviceId)
                .set("system", system)
                .set("lastSeen", Instant.now())
                .setOnInsert("firstSeen", Instant.now());

        // Actualizar solo si no es null
        if (type != null && !type.isBlank())         update.set("type", type);
        if (hostname != null && !hostname.isBlank()) update.set("hostname", hostname);
        if (latitude != null)                        update.set("latitude", latitude);
        if (longitude != null)                       update.set("longitude", longitude);
        if (locationName != null && !locationName.isBlank()) update.set("locationName", locationName);

        // ── Consolidación de IPs ─────────────────────────────────────────────
        // `ip` guarda la última IP reportada (backward compat con frontend).
        // `ipInterfaces.<tipo>` acumula TODAS las IPs sin sobreescribir las anteriores:
        //   {"privada": "192.168.100.8", "publica": "187.188.66.56"}
        if (ip != null && !ip.isBlank()) {
            update.set("ip", ip);
            String ifaceKey = classifyIp(ip);                       // "privada" | "publica" | "loopback"
            update.set("ipInterfaces." + ifaceKey, ip);             // $set anidado → no borra las otras
        }

        // ── Detectar encendido/apagado ───────────────────────────────────────
        if (eventType != null) {
            switch (eventType) {
                case "SYSTEM_STARTUP", "SYSTEM_BOOT_INFO" ->
                    update.set("lastPowerOn", Instant.now());
                case "SYSTEM_SHUTDOWN", "SYSTEM_SHUTDOWN_INIT" ->
                    update.set("lastPowerOff", Instant.now());
            }
        }

        try {
            mongoTemplate.upsert(query, update, Device.class);
        } catch (Exception e) {
            log.warn("[DeviceRegistry] Error registrando dispositivo {}: {}", deviceId, e.getMessage());
        }
    }

    /**
     * Clasifica una IP según su rango:
     * <ul>
     *   <li><b>privada</b>: 10.x, 172.16-31.x, 192.168.x, 127.x, 169.254.x</li>
     *   <li><b>loopback</b>: ::1</li>
     *   <li><b>publica</b>: cualquier otra</li>
     * </ul>
     */
    private static String classifyIp(String ip) {
        if (ip == null || ip.isBlank()) return "desconocida";
        String t = ip.trim();
        if (t.equals("::1") || t.equals("0:0:0:0:0:0:0:1")) return "loopback";
        if (t.startsWith("127.") || t.startsWith("169.254.")) return "privada";
        if (t.startsWith("10.") || t.startsWith("192.168."))  return "privada";
        if (t.startsWith("172.")) {
            try {
                String[] parts = t.split("\\.");
                int second = Integer.parseInt(parts[1]);
                if (second >= 16 && second <= 31) return "privada";
            } catch (Exception ignored) { /* sigue a publica */ }
        }
        return "publica";
    }

    // Devuelve todos los dispositivos de un tenant con status calculado, filtrando opcionalmente por system y status
    public List<Device> getAllDevices(ObjectId tenantId, String system, String statusFilter) {
        List<Criteria> cs = new java.util.ArrayList<>();
        cs.add(Criteria.where("tenantId").is(tenantId));

        if (system != null && !system.isBlank())
            cs.add(Criteria.where("system").is(system.trim().toUpperCase(java.util.Locale.ROOT)));

        Query query = new Query(new Criteria().andOperator(cs.toArray(new Criteria[0])));
        List<Device> devices = mongoTemplate.find(query, Device.class);

        Instant threshold = Instant.now().minus(OFFLINE_THRESHOLD_MINUTES, ChronoUnit.MINUTES);

        for (Device d : devices) {
            if (d.getLastSeen() != null && d.getLastSeen().isAfter(threshold)) {
                d.setStatus("ONLINE");
            } else {
                d.setStatus("OFFLINE");
            }
        }

        // Filtrar por status después de calcularlo dinámicamente
        if (statusFilter != null && !statusFilter.isBlank()) {
            String sf = statusFilter.trim().toUpperCase(java.util.Locale.ROOT);
            devices = devices.stream().filter(d -> sf.equals(d.getStatus())).toList();
        }

        return devices;
    }

    // Resumen: total, online, offline por tipo — con filtros opcionales de system y status
    public Map<String, Object> getSummary(ObjectId tenantId, String system, String statusFilter) {
        List<Device> devices = getAllDevices(tenantId, system, statusFilter);

        long total   = devices.size();
        long online  = devices.stream().filter(d -> "ONLINE".equals(d.getStatus())).count();
        long offline = total - online;

        long servers  = devices.stream().filter(d -> "SERVER".equals(d.getType())).count();
        long pcs      = devices.stream().filter(d -> "PC".equals(d.getType())).count();
        long switches = devices.stream().filter(d -> "SWITCH".equals(d.getType())).count();
        long cameras  = devices.stream().filter(d -> "CAMERA".equals(d.getType())).count();

        return Map.of(
                "total",    total,
                "online",   online,
                "offline",  offline,
                "byType",   Map.of(
                        "servers",  servers,
                        "pcs",      pcs,
                        "switches", switches,
                        "cameras",  cameras
                ),
                "devices",  devices
        );
    }

}
