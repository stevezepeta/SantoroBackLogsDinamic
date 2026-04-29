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

    // Registra o actualiza un dispositivo cuando llega un log
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
        if (type != null && !type.isBlank()) update.set("type", type);
        if (ip != null && !ip.isBlank())             update.set("ip", ip);
        if (hostname != null && !hostname.isBlank()) update.set("hostname", hostname);
        if (latitude != null)                        update.set("latitude", latitude);
        if (longitude != null)                       update.set("longitude", longitude);
        if (locationName != null && !locationName.isBlank()) update.set("locationName", locationName);

        // Detectar encendido/apagado
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
