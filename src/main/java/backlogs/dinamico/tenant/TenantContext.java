package backlogs.dinamico.tenant;

import lombok.*;
import org.bson.types.ObjectId;

public final class TenantContext {

    // Contexto por request (ThreadLocal) con valor por defecto vacío
    private static final ThreadLocal<Ctx> CURRENT = ThreadLocal.withInitial(Ctx::empty);

    private TenantContext() {}

    /** Datos del contexto (amplíalo si lo necesitas). */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder(toBuilder = true)
    public static class Ctx {

        // --- mínimos / multitenant ---
        private ObjectId tenantId;
        private ObjectId organizationId;

        // --- opcionales (enriquecidos) ---
        private ObjectId userId;
        private ObjectId personId;
        private String   email;
        private String   name;

        // --- otros campos útiles ---
        private ObjectId systemId;
        private ObjectId environmentId;
        private String   dbName;
        private String   collectionSuffix;

        static Ctx empty() { return new Ctx(); }

        public boolean hasTenant() { return tenantId != null; }
    }

    // ------------ API básica ------------
    /** Establece todo el contexto. */
    public static void set(Ctx ctx) {
        CURRENT.set(ctx != null ? ctx : Ctx.empty());
    }

    public static Ctx get() {
        return CURRENT.get();
    }

    /** Limpia el contexto del request actual. */
    public static void clear() {
        CURRENT.remove();
    }

    // ------------ Tenant helpers ------------
    public static void setTenantId(ObjectId id) {
        CURRENT.get().setTenantId(id);
    }

    public static ObjectId getTenantId() {
        return CURRENT.get().getTenantId();
    }

    /** Útil para evitar repetir validaciones en services. */
    public static ObjectId requireTenantId() {
        ObjectId id = getTenantId();
        if (id == null) throw new IllegalStateException("Tenant no resuelto en contexto");
        return id;
    }

    public static void setTenantIdHex(String hex) {
        if (hex != null && ObjectId.isValid(hex)) setTenantId(new ObjectId(hex));
    }

    public static String getTenantIdHex() {
        ObjectId id = getTenantId();
        return (id != null) ? id.toHexString() : null;
    }

    // ------- OrganizationId helpers ------
    public static void setOrganizationId(ObjectId id) {
        CURRENT.get().setOrganizationId(id);
    }

    public static ObjectId getOrganizationId() {
        return CURRENT.get().getOrganizationId();
    }

    public static void setOrganizationIdHex(String hex) {
        if (hex != null && ObjectId.isValid(hex)) setOrganizationId(new ObjectId(hex));
    }

    public static String getOrganizationIdHex() {
        ObjectId id = getOrganizationId();
        return (id != null) ? id.toHexString() : null;
    }

    // ------------ System/Environment helpers ------------
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

    // ------------ Routing helpers (db/collection) ------------
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

    // ------------ Convenience: set rápido para ingesta (API Key) ------------
    public static void setForIngest(ObjectId tenantId, ObjectId systemId, ObjectId environmentId) {
        set(Ctx.builder()
                .tenantId(tenantId)
                .systemId(systemId)
                .environmentId(environmentId)
                .build());
    }
}
