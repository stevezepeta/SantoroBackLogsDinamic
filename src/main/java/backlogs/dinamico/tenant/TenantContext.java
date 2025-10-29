package backlogs.dinamico.tenant;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;

public final class TenantContext {

    // Contexto por request (ThreadLocal) con valor por defecto vacío
    private static final ThreadLocal<Ctx> CURRENT = ThreadLocal.withInitial(Ctx::new);

    private TenantContext() {}

    /** Datos del contexto (amplíalo si lo necesitas). */
    @Getter @Setter @Builder
    public static class Ctx {
        // --- mínimos / multitenant ---
        private ObjectId tenantId;

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

        /** ctor por defecto -> útil para withInitial y builder */
        public Ctx() {}

        /** ctor mínimo por comodidad */
        public Ctx(ObjectId tenantId) { this.tenantId = tenantId; }

        /**
         * SHIM: ctor completo para compatibilidad con llamadas antiguas.
         * Puedes marcarlo como @Deprecated y borrarlo cuando refactors terminen.
         */
        @Deprecated
        public Ctx(ObjectId tenantId, ObjectId userId, ObjectId personId, String email, String name,
                   ObjectId systemId, ObjectId environmentId, String dbName, String collectionSuffix) {
            this.tenantId = tenantId;
            this.userId = userId;
            this.personId = personId;
            this.email = email;
            this.name = name;
            this.systemId = systemId;
            this.environmentId = environmentId;
            this.dbName = dbName;
            this.collectionSuffix = collectionSuffix;
        }

        public boolean hasTenant() { return tenantId != null; }
    }

    // ------------ API básica ------------
    /** Establece todo el contexto. */
    public static void set(Ctx ctx) {
        CURRENT.set(ctx != null ? ctx : new Ctx());
    }

    /** Conveniencia: sólo tenant. */
    public static void set(ObjectId tenantId) {
        CURRENT.set(new Ctx(tenantId));
    }

    /** Conveniencia: "completo" usando builder (evita ctors largos). */
    public static void set(ObjectId tenantId, ObjectId userId, ObjectId personId, String email, String name,
                           ObjectId systemId, ObjectId environmentId, String dbName, String collectionSuffix) {
        CURRENT.set(Ctx.builder()
                .tenantId(tenantId)
                .userId(userId)
                .personId(personId)
                .email(email)
                .name(name)
                .systemId(systemId)
                .environmentId(environmentId)
                .dbName(dbName)
                .collectionSuffix(collectionSuffix)
                .build());
    }

    public static Ctx get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    // ------------ Helpers convenientes ------------
    public static void setTenantId(ObjectId id) {
        CURRENT.get().setTenantId(id);
    }

    public static ObjectId getTenantId() {
        return CURRENT.get().getTenantId();
    }

    // tenantId como hex
    public static void setTenantIdHex(String hex) {
        if (hex != null && ObjectId.isValid(hex)) {
            setTenantId(new ObjectId(hex));
        }
    }

    public static String getTenantIdHex() {
        ObjectId id = getTenantId();
        return (id != null) ? id.toHexString() : null;
    }

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
}
