package backlogs.dinamico.error;

import org.springframework.dao.DuplicateKeyException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MongoErrors {

    private static final Pattern VAL_PATTERN = Pattern.compile("dup key.*\\{\\s*:?\\s*\"?([\\w.]+)?\"?\\s*:\\s*\"?([^\"}]+)\"?\\s*}\\s*$", Pattern.CASE_INSENSITIVE);

    private MongoErrors() {

    }

    public static String buildDuplicateMessage(DuplicateKeyException ex, String defaultField) {
        String msg = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        if (msg == null) return "Duplicate key";

        Matcher m = VAL_PATTERN.matcher(msg);
        if (m.find()) {
            String field = m.group(1) != null ? m.group(1) : defaultField;
            String value = m.group(2);
            return String.format("%s '%s' already exists", field != null ? field : "value", value);
        }
        // Fallback por nombre de índice
        if (msg.contains("ux_org_domain")) return "domain already exists";
        if (msg.contains("ux_org_code"))   return "code already exists";
        if (msg.contains("ux_org_slug"))   return "slug already exists";
        return "Duplicate key";
    }

}
