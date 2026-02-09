package backlogs.dinamico.model.core;


import backlogs.dinamico.model.base.BaseEntity;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Field;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "organizations")
@CompoundIndexes({
        @CompoundIndex(name = "ux_org_domain", def = "{'domain': 1}", unique = true, sparse = true),
        @CompoundIndex(name = "ux_org_code",   def = "{'code': 1}",   unique = true, sparse = true),
        @CompoundIndex(name = "ux_org_slug",   def = "{'slug': 1}",   unique = true, sparse = true)
})
public class Organization extends BaseEntity {

    @Field("rate_limit")
    private RateLimitPolicy rateLimit;

    @Field("name")
    private String name;

    // Dominio principal
    @Field("domain")
    private String domain;

    @Field("code")
    private String code;

    // Codigo corto de la empresa
    @Field("slug")
    private String slug;

    @Field("status")
    private String status; // active|disabled

    @Field("settings")
    private Settings settings; // Retención, zona horaria, límites, etc.

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Settings {
        @Field("timezone")
        private String timezone;      // p.ej. "Mexico_City"
        @Field("retentionDays")
        private Integer retentionDays; // p.ej. 90
    }

}