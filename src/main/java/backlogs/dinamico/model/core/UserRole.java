package backlogs.dinamico.model.core;

import backlogs.dinamico.model.base.BaseEntity;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.HashSet;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
@Document(collection = "user_roles")
@CompoundIndexes({
        @CompoundIndex(
                name = "ux_user_role",
                def = "{ 'tenant_id': 1, 'user_id': 1, 'role_id': 1 }",
                unique = true),
        @CompoundIndex(
                name = "ix_user_role_user",
                def = "{ 'tenant_id': 1, 'user_id': 1 }"
        )
})
public class UserRole extends BaseEntity {

    @NotNull
    @Field("tenant_id")
    private ObjectId tenantId;

    @NotNull
    @Field("user_id")
    private ObjectId userId;

    @NotNull
    @Field("role_id")
    private ObjectId roleId;

    @Builder.Default
    @Field("allowed_systems")
    private Set<String> allowedSystems = new HashSet<>();

    /**
     * Filtros de visibilidad de logs para este usuario en este rol.
     * Cuando están vacíos (null o colecciones vacías) = sin restricción.
     * Cuando tienen valores = solo se muestran registros que coincidan.
     *
     * Ejemplo VIEWER cliente de TRUSTVALUE:
     *   allowedOutcomes  = ["SUCCESS", "APPROVED"]
     *   allowedStatuses  = []          (cualquier status)
     *   allowedSeverities = ["INFO"]
     */
    @Field("log_filters")
    private LogFilter logFilters;

    // ── Embedded: filtros de logs ─────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LogFilter {

        /**
         * Valores de `outcome` permitidos.
         * Vacío = sin restricción.
         * Ejemplo: ["SUCCESS", "APPROVED"]
         */
        @Builder.Default
        @Field("allowed_outcomes")
        private Set<String> allowedOutcomes = new HashSet<>();

        /**
         * Valores de `status` permitidos.
         * Vacío = sin restricción.
         * Ejemplo: ["OK"]
         */
        @Builder.Default
        @Field("allowed_statuses")
        private Set<String> allowedStatuses = new HashSet<>();

        /**
         * Valores de `severity` permitidos.
         * Vacío = sin restricción.
         * Ejemplo: ["INFO", "DEBUG"]
         */
        @Builder.Default
        @Field("allowed_severities")
        private Set<String> allowedSeverities = new HashSet<>();

        /**
         * Tipos de evento (`eventType`) permitidos.
         * Vacío = sin restricción.
         * Ejemplo: ["AUTH_LOGIN", "APP_START"]
         */
        @Builder.Default
        @Field("allowed_event_types")
        private Set<String> allowedEventTypes = new HashSet<>();

        /** Devuelve true si no hay ninguna restricción activa. */
        public boolean isEmpty() {
            return isNullOrEmpty(allowedOutcomes)
                    && isNullOrEmpty(allowedStatuses)
                    && isNullOrEmpty(allowedSeverities)
                    && isNullOrEmpty(allowedEventTypes);
        }

        private static boolean isNullOrEmpty(Set<String> s) {
            return s == null || s.isEmpty();
        }
    }

    // ── Factory methods ───────────────────────────────────────────────────────

    public static UserRole of(ObjectId tenantId, ObjectId userId, ObjectId roleId) {
        return UserRole.builder()
                .tenantId(tenantId)
                .userId(userId)
                .roleId(roleId)
                .build();
    }

    public static UserRole of(ObjectId tenantId, ObjectId userId, ObjectId roleId,
                              Set<String> allowedSystems) {
        return UserRole.builder()
                .tenantId(tenantId)
                .userId(userId)
                .roleId(roleId)
                .allowedSystems(allowedSystems != null ? allowedSystems : new HashSet<>())
                .build();
    }

    public static UserRole of(ObjectId tenantId, ObjectId userId, ObjectId roleId,
                              Set<String> allowedSystems, LogFilter logFilters) {
        return UserRole.builder()
                .tenantId(tenantId)
                .userId(userId)
                .roleId(roleId)
                .allowedSystems(allowedSystems != null ? allowedSystems : new HashSet<>())
                .logFilters(logFilters)
                .build();
    }
}