package backlogs.dinamico.tenant;

import lombok.*;
import org.bson.types.ObjectId;

public final class TenantContext {

    private static final ThreadLocal<Ctx> CURRENT =
            ThreadLocal.withInitial(Ctx::empty);

    private TenantContext() {}

    public static void setRequiresRotation(boolean b) {
    }

    // ================= CONTEXT =================
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder(toBuilder = true)
    public static class Ctx {

        // --- multitenant ---
        private ObjectId tenantId;
        private ObjectId organizationId;

        // --- JWT ---
        private ObjectId userId;
        private ObjectId personId;
        private String email;
        private String name;

        // --- auth origin ---
        // JWT | API_KEY
        private String authKind;

        // --- ingest (API KEY) ---
        private ObjectId systemId;
        private ObjectId environmentId;

        // --- routing ---
        private String dbName;
        private String collectionSuffix;

        static Ctx empty() {
            return new Ctx();
        }

        public boolean isJwt() {
            return "JWT".equalsIgnoreCase(authKind) || userId != null;
        }

        public boolean isApiKey() {
            return "API_KEY".equalsIgnoreCase(authKind) || systemId != null;
        }
    }

    // ================= CORE =================
    public static void set(Ctx ctx) {
        CURRENT.set(ctx != null ? ctx : Ctx.empty());
    }

    public static Ctx get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    // ================= TENANT HEX HELPERS =================
    public static void setTenantIdHex(String hex) {
        if (hex != null && ObjectId.isValid(hex)) {
            setTenantId(new ObjectId(hex));
        }
    }

    public static String getTenantIdHex() {
        ObjectId id = getTenantId();
        return (id != null) ? id.toHexString() : null;
    }

    // ================= ORG HEX HELPERS =================
    public static void setOrganizationIdHex(String hex) {
        if (hex != null && ObjectId.isValid(hex)) {
            setOrganizationId(new ObjectId(hex));
        }
    }

    public static String getOrganizationIdHex() {
        ObjectId id = getOrganizationId();
        return (id != null) ? id.toHexString() : null;
    }

    // ================= TENANT =================
    public static void setTenantId(ObjectId id) {
        CURRENT.get().setTenantId(id);
    }

    public static ObjectId getTenantId() {
        return CURRENT.get().getTenantId();
    }

    public static ObjectId requireTenantId() {
        ObjectId id = getTenantId();
        if (id == null) {
            throw new IllegalStateException("Tenant no resuelto en contexto");
        }
        return id;
    }

    // ================= ORGANIZATION =================
    public static void setOrganizationId(ObjectId id) {
        CURRENT.get().setOrganizationId(id);
    }

    public static ObjectId getOrganizationId() {
        return CURRENT.get().getOrganizationId();
    }

    // ================= AUTH KIND =================
    public static void setAuthKind(String kind) {
        CURRENT.get().setAuthKind(kind);
    }

    public static String getAuthKind() {
        return CURRENT.get().getAuthKind();
    }

    public static boolean isJwt() {
        return CURRENT.get().isJwt();
    }

    public static boolean isApiKey() {
        return CURRENT.get().isApiKey();
    }

    // ================= SYSTEM / ENV =================
    public static void setSystemId(ObjectId id) {
        CURRENT.get().setSystemId(id);
    }

    public static ObjectId getSystemId() {
        return CURRENT.get().getSystemId();
    }

    public static void setEnvironmentId(ObjectId id) {
        CURRENT.get().setEnvironmentId(id);
    }

    public static ObjectId getEnvironmentId() {
        return CURRENT.get().getEnvironmentId();
    }

    // ================= ROUTING =================
    public static void setDbName(String db) {
        CURRENT.get().setDbName(db);
    }

    public static String getDbName() {
        return CURRENT.get().getDbName();
    }

    public static void setCollectionSuffix(String s) {
        CURRENT.get().setCollectionSuffix(s);
    }

    public static String getCollectionSuffix() {
        return CURRENT.get().getCollectionSuffix();
    }

    // ================= INGEST HELPER =================
    public static void setForIngest(
            ObjectId tenantId,
            ObjectId systemId,
            ObjectId environmentId
    ) {
        set(Ctx.builder()
                .tenantId(tenantId)
                .organizationId(tenantId)
                .systemId(systemId)
                .environmentId(environmentId)
                .authKind("API_KEY")
                .build());
    }
}
