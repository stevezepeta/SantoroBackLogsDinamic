package backlogs.dinamico.model.log;

import lombok.*;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "devices_registry")
@CompoundIndexes({
        @CompoundIndex(name = "idx_device_tenant_deviceId", def = "{'tenantId': 1, 'deviceId': 1}", unique = true)
})
public class Device {

    @Id
    private ObjectId id;

    private ObjectId tenantId;

    private String deviceId;
    private String hostname;
    private String system;
    private String type;

    /** IP principal (última reportada) — se mantiene para compatibilidad con el mapa del frontend */
    private String ip;

    /**
     * Mapa de interfaces de red detectadas automáticamente.
     * Clave: "privada" | "publica" | "loopback"
     * Valor: la IP correspondiente.
     * Ejemplo: {"privada": "192.168.100.8", "publica": "187.188.66.56"}
     */
    @Builder.Default
    private Map<String, String> ipInterfaces = new java.util.LinkedHashMap<>();

    private Double latitude;
    private Double longitude;
    private String locationName;

    private Instant firstSeen;
    private Instant lastSeen;

    private Instant lastPowerOn;
    private Instant lastPowerOff;

    private String status;

}
