package backlogs.dinamico.service.normalization;

import backlogs.dinamico.model.log.LogEvent;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class SeverityNormalizer {

    /**
     * Normaliza severities externas (Elyctis: LOW/MEDIUM/CRITICAL)
     * a severities internas (INFO/WARN/ERROR/FATAL/DEBUG).
     *
     * Regla clave:
     * - Si status == "ERROR" => severity normalizada = "ERROR"
     * - Si outcome/reason.code contiene "ERR" => severity normalizada = "ERROR"
     * - Si no, usa severityRaw: LOW->INFO, MEDIUM->WARN, CRITICAL->ERROR
     */
    public String normalize(LogEvent e) {

        String raw = up(e.getSeverityRaw());
        String status = up(e.getStatus());
        String outcome = up(e.getOutcome());

        // Reason.code también ayuda mucho (Elyctis lo trae)
        String reasonCode = "";
        if (e.getReason() != null) {
            reasonCode = up(e.getReason().getCode());
        }

        // 1) Si el status ya indica error, manda a ERROR
        if ("ERROR".equals(status) || "FATAL".equals(status)) {
            return "ERROR";
        }

        // 2) Si outcome/reason trae "ERR", considerarlo ERROR operacional
        if (containsErr(outcome) || containsErr(reasonCode)) {
            return "ERROR";
        }

        // 3) Si raw ya viene en tu estándar, respétalo
        switch (raw) {
            case "DEBUG":
            case "TRACE":
                return "DEBUG";
            case "INFO":
                return "INFO";
            case "WARN":
            case "WARNING":
                return "WARN";
            case "ERROR":
                return "ERROR";
            case "FATAL":
                return "FATAL";
        }

        // 4) Mapeo Elyctis
        return switch (raw) {
            case "LOW" -> "INFO";
            case "MEDIUM" -> "WARN";
            case "CRITICAL" -> "ERROR";
            default -> "INFO"; // fallback seguro
        };
    }

    private boolean containsErr(String s) {
        return s != null && !s.isBlank() && s.contains("ERR");
    }

    private String up(String s) {
        return (s == null) ? "" : s.trim().toUpperCase(Locale.ROOT);
    }
}
