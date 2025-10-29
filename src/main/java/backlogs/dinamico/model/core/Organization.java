package backlogs.dinamico.model.core;


import backlogs.dinamico.model.base.BaseEntity;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.bson.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;


@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@org.springframework.data.mongodb.core.mapping.Document(collection = "organizations")
@CompoundIndexes({
        @CompoundIndex(name = "ix_org_status", def = "{ 'status': 1 }")
})
public class Organization extends BaseEntity {

    private String name;

    // Dominio principal
    @Indexed(name = "ux_org_domain", unique = true, sparse = true)
    private String domain;

    @Indexed(name = "ux_org_code", unique = true, sparse = true)
    private String code;

    // Codigo corto de la empresa
    @Indexed(name = "ux_org_slug", unique = true, sparse = true)
    private String slug;

    private String status; // active|disabled

    private Document settings; // Retención, zona horaria, límites, etc.

}