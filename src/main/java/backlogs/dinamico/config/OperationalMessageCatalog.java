package backlogs.dinamico.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "operational")
public class OperationalMessageCatalog {

    private Map<String, Map<String, Map<String, OperationalMessage>>> catalog = new HashMap<>();

    public OperationalMessage find(String system, String eventCode, String status) {
        if (system == null || eventCode == null || status == null) {
            return null;
        }

        String systemNorm = system.trim().toUpperCase();
        String eventCodeNorm = eventCode.trim().toUpperCase();
        String statusNorm = status.trim().toUpperCase();

        Map<String, Map<String, OperationalMessage>> systemCatalog = catalog.get(systemNorm);
        if (systemCatalog == null) {
            return null;
        }

        Map<String, OperationalMessage> eventCatalog = systemCatalog.get(eventCodeNorm);
        if (eventCatalog == null) {
            return null;
        }

        OperationalMessage message = eventCatalog.get(statusNorm);
        if (message == null) {
            // Fallback a un estado genérico de error si se pidió REJECTED/ERROR/FAILED
            if (statusNorm.equals("REJECTED") || statusNorm.equals("FAILED")) {
                message = eventCatalog.get("ERROR");
            }
        }

        return message;
    }

    /**
     * Mensaje operativo individual.
     */
    @Data
    public static class OperationalMessage {
        private String title;
        private String description;
        private String action;
        private String icon;
    }
}
