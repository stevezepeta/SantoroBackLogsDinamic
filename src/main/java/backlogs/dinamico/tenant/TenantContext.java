package backlogs.dinamico.tenant;


import lombok.*;
import org.bson.types.ObjectId;

@Getter @Setter @ToString
public class TenantContext {
  private ObjectId tenantId;
  private ObjectId systemId;
  private ObjectId environmentId;
  private String dbName;          
  private String collectionSuffix; 
}
