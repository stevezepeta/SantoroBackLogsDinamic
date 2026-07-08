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

    public void upsertFromLog(ObjectId tenantId, String deviceId, String system,
                              String type, String ip, String hostname,
                              Double latitude, Double longitude,
                              String locationName, String eventType) {

        // ══════════════════════════════════════════════════════════════════════════════
        // FILTRO DE INTEGRIDAD ESTRICTO: Validar deviceId antes de registrar
        // ══════════════════════════════════════════════════════════════════════════════
        if (tenantId == null) return;
        if (deviceId == null || deviceId.isBlank()) return;
        
        // Rechazar deviceId que sea solo un guion "-"
        if ("-".equals(deviceId.trim())) {
            log.debug("[DeviceRegistry] Rechazado deviceId inválido: '-'");
            return;
        }
        
        // Rechazar deviceId con longitud menor a 5 caracteres (no es un hash/UUID válido)
        if (deviceId.trim().length() < 5) {
            log.debug("[DeviceRegistry] Rechazado deviceId demasiado corto: '{}' (len={})", 
                     deviceId, deviceId.trim().length());
            return;
        }

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

        // ══════════════════════════════════════════════════════════════════════════════
        // FILTRO DE INTEGRIDAD: Excluir dispositivos con deviceId inválido/corrupto
        // para evitar marcadores fantasma en el mapa (caseId vacío, nulo o con "-")
        // ══════════════════════════════════════════════════════════════════════════════
        cs.add(Criteria.where("deviceId").exists(true).ne(null));
        cs.add(Criteria.where("deviceId").ne(""));
        cs.add(Criteria.where("deviceId").ne("-"));
        // Filtrar por longitud mínima (regex: al menos 5 caracteres alfanuméricos)
        cs.add(Criteria.where("deviceId").regex("^.{5,}$"));

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
