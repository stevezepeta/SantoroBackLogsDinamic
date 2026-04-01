package backlogs.dinamico.model.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

@Document("flows_config")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@CompoundIndex(name = "ux_tenant_flow", def = "{'tenant_id': 1, 'flow_id': 1}", unique = true)
public class FlowConfig {

    @Field("tenant_id")
    private ObjectId tenantId;

    @Field("flow_id")
    private String flowId;

    @Field("name")
    private String name;

    @Field("icon")
    private String icon;

    @Field("color")
    private String color;

    @Field("modules")
    private List<ModuleItem> modules;

    @Field("created_at")
    private Instant createdAt;

    @Field("update_at")
    private Instant updateAt;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ModuleItem {

        @Field("id")
        private String id;

        @Field("name")
        private String name;

        @Field("icon")
        private String icon;

        @Field("route")
        private String route;

        @Field("order")
        private Integer order;

        @Field("active")
        private Boolean active;

        @Field("perms")
        private List<String> perms;

    }

}
