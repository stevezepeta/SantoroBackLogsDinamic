package backlogs.dinamico.tenant;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;

public final class TenantContext {

    private TenantContext() {}

    // Contexto por request (Servlet/MVC)
    private static final ThreadLocal<Ctx> CURRENT = new ThreadLocal<>();

    /** Datos del contexto (amplía si lo necesitas). */
    @Getter @Setter @Builder
    public static class Ctx {
        private ObjectId tenantId;
        private ObjectId systemId;
        private ObjectId environmentId;
        private String dbName;
        private String collectionSuffix;
    }

    // ------------ API básica ------------
    public static void set(Ctx ctx) { CURRENT.set(ctx); }
    public static Ctx get() { return CURRENT.get(); }
    public static void clear() { CURRENT.remove(); }

    // ------------ helpers convenientes ------------
    private static Ctx orCreate() {
        Ctx c = CURRENT.get();
        if (c == null) {
            c = Ctx.builder().build();
            CURRENT.set(c);
        }
        return c;
    }

    // tenantId como ObjectId
    public static void setTenantId(ObjectId id) { orCreate().setTenantId(id); }
    public static ObjectId getTenantId() { return (CURRENT.get() != null) ? CURRENT.get().getTenantId() : null; }

    // tenantId como hex (compatibilidad con tu HEAD anterior)
    public static void setTenantIdHex(String hex) {
        if (hex != null && ObjectId.isValid(hex)) setTenantId(new ObjectId(hex));
    }
    public static String getTenantIdHex() {
        ObjectId id = getTenantId();
        return id != null ? id.toHexString() : null;
    }

    public static void setSystemId(ObjectId id) { orCreate().setSystemId(id); }
    public static ObjectId getSystemId() { return (CURRENT.get() != null) ? CURRENT.get().getSystemId() : null; }

    public static void setEnvironmentId(ObjectId id) { orCreate().setEnvironmentId(id); }
    public static ObjectId getEnvironmentId() { return (CURRENT.get() != null) ? CURRENT.get().getEnvironmentId() : null; }

    public static void setDbName(String db) { orCreate().setDbName(db); }
    public static String getDbName() { return (CURRENT.get() != null) ? CURRENT.get().getDbName() : null; }

    public static void setCollectionSuffix(String s) { orCreate().setCollectionSuffix(s); }
    public static String getCollectionSuffix() { return (CURRENT.get() != null) ? CURRENT.get().getCollectionSuffix() : null; }
}
