package backlogs.dinamico.repository.config;

import backlogs.dinamico.model.config.FlowConfig;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface FlowConfigRepository extends MongoRepository<FlowConfig, ObjectId> {

    Optional<FlowConfig> findByTenantIdAndFlowId(ObjectId tenantId, String flowId);

}
