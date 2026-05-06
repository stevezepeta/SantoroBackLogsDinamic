package backlogs.dinamico.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Provee los umbrales de alerta configurables por sistema.
 * Lee de application.properties con fallback a valores por defecto.
 *
 * Formato en properties:
 *   alert.thresholds.NOMBRE_SISTEMA.warn=0.10
 *   alert.thresholds.NOMBRE_SISTEMA.crit=0.20
 */
@Slf4j
@Component
public class AlertThresholdConfig {

    private static final double DEFAULT_WARN = 0.05;
    private static final double DEFAULT_CRIT = 0.15;

    private final Environment env;

    @Autowired
    public AlertThresholdConfig(Environment env) {
        this.env = env;
    }

    public record Thresholds(double warn, double crit) {}

    /**
     * Obtiene los umbrales para un sistema específico.
     * Si no está configurado, usa los valores por defecto.
     */
    public Thresholds getThresholds(String system) {
        if (system == null || system.isBlank()) {
            return getDefault();
        }

        String key = system.trim().toUpperCase()
                .replace(" ", "_")
                .replace("-", "_");

        double warn = getDouble(
                "alert.thresholds." + key + ".warn",
                getDouble("alert.thresholds.default.warn", DEFAULT_WARN)
        );
        double crit = getDouble(
                "alert.thresholds." + key + ".crit",
                getDouble("alert.thresholds.default.crit", DEFAULT_CRIT)
        );

        // Validar que crit > warn
        if (crit <= warn) {
            log.warn("[AlertThreshold] Sistema '{}': crit ({}) <= warn ({}), ajustando crit a warn + 0.10",
                    system, crit, warn);
            crit = warn + 0.10;
        }

        log.debug("[AlertThreshold] Sistema '{}': warn={}% crit={}%",
                system, warn * 100, crit * 100);

        return new Thresholds(warn, crit);
    }

    public Thresholds getDefault() {
        double warn = getDouble("alert.thresholds.default.warn", DEFAULT_WARN);
        double crit = getDouble("alert.thresholds.default.crit", DEFAULT_CRIT);
        return new Thresholds(warn, crit);
    }

    private double getDouble(String key, double defaultValue) {
        try {
            String val = env.getProperty(key);
            if (val != null && !val.isBlank()) {
                return Double.parseDouble(val.trim());
            }
        } catch (NumberFormatException e) {
            log.warn("[AlertThreshold] Valor inválido para '{}', usando default {}", key, defaultValue);
        }
        return defaultValue;
    }
}