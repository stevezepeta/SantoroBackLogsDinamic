package backlogs.dinamico.service.logs;

import java.text.Normalizer;
import java.util.Locale;

public class LogNormalizationUtils {

    private LogNormalizationUtils() {}

    public static String buildMessageKey(String message, String reasonDescription) {

        String base = firstNonBlank(message, reasonDescription);
        if (base == null) return null;

        String s = base.trim();

        // bajar a "Texto comparable"
        s = stripAccents(s).toLowerCase(Locale.ROOT);

        // normalizar patrones variables
        s = s.replaceAll("\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b", "{uuid}");
        s = s.replaceAll("\\b[0-9a-f]{24}\\b", "{oid}");                // ObjectId
        s = s.replaceAll("\\b(?:trace|req|request)[-_ ]?id\\b\\s*[:=]?\\s*\\S+", "requestid:{id}");
        s = s.replaceAll("\\bpass-\\d{4}-\\d+\\b", "pass-{id}");
        s = s.replaceAll("\\bbio-\\d{4}-\\d+\\b", "bio-{id}");
        s = s.replaceAll("\\btrx\\d+\\b", "trx{n}");
        s = s.replaceAll("\\busr\\w+\\b", "usr{n}");
        s = s.replaceAll("\\b\\d+\\b", "{n}");

        // colapsar espacios
        s = s.replaceAll("\\s+", " ").trim();

        // recortar a algo estable
        if (s.length() > 160) s = s.substring(0, 160);

        return s.isBlank() ? null : s;
    }

    public static String normalizeSeverity(String severityRaw) {
        if (severityRaw == null) return null;
        String s = severityRaw.trim().toUpperCase(Locale.ROOT);

        // ya normalizados
        if (s.equals("INFO") || s.equals("WARN") || s.equals("ERROR") || s.equals("FATAL")) return s;

        // Elyctis (tu caso)
        return switch (s) {
            case "LOW"      -> "INFO";
            case "MEDIUM"   -> "WARN";
            case "CRITICAL" -> "ERROR";
            default         -> s; // si llega algo nuevo no lo rompes
        };
    }

    public static boolean computeIsError(String severityNorm, String status, String outcome) {
        if (severityNorm != null && (severityNorm.equalsIgnoreCase("ERROR") || severityNorm.equalsIgnoreCase("FATAL"))) return true;
        if (outcome != null && outcome.equalsIgnoreCase("FAILURE")) return true;
        if (status != null && (status.equalsIgnoreCase("REJECTED") || status.equalsIgnoreCase("ERROR"))) return true;
        return outcome != null && outcome.toUpperCase(Locale.ROOT).contains("ERR");
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    private static String stripAccents(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "");
    }

}
