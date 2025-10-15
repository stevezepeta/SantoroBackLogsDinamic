package backlogs.dinamico.tenant;

import org.bson.types.ObjectId;

public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
    private TenantContext() {

    }

    public static void set(String tenantIdHex) {
        CURRENT.set(tenantIdHex);
    }

    public static String get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static ObjectId getAsObjectId() {
        String v = get();
        return (v != null && ObjectId.isValid(v)) ? new ObjectId(v) : null;
    }

}
