package backlogs.dinamico.service.logs;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normaliza el eventType recibido separando la categoría del código numérico.
 *
 * Ejemplos:
 *   APP_EVENT_1034  →  category: "APP_EVENT",  code: "1034"
 *   SYS_EVENT_99   →  category: "SYS_EVENT",   code: "99"
 *   LOGIN           →  category: "LOGIN",        code: null
 */
@Component
public class EventTypeNormalizer {

    // Captura todo antes del último "_NÚMERO" al final
    private static final Pattern NUMERIC_SUFFIX = Pattern.compile("^(.+)_(\\d+)$");

    public record NormalizedEventType(
            String category,   // eventType que se guarda en BD  → "APP_EVENT"
            String code,       // sufijo numérico                → "1034"
            String raw         // valor original tal cual        → "APP_EVENT_1034"
    ) {}

    public NormalizedEventType normalize(String rawEventType) {
        if (rawEventType == null || rawEventType.isBlank()) {
            return new NormalizedEventType("UNKNOWN", null, rawEventType);
        }

        String upper = rawEventType.trim().toUpperCase();
        Matcher m = NUMERIC_SUFFIX.matcher(upper);

        if (m.matches()) {
            return new NormalizedEventType(
                    m.group(1),   // "APP_EVENT"
                    m.group(2),   // "1034"
                    upper         // "APP_EVENT_1034"
            );
        }

        // No tiene sufijo numérico → lo deja como está
        return new NormalizedEventType(upper, null, upper);
    }
}