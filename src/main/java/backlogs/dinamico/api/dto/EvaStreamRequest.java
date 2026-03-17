package backlogs.dinamico.api.dto;

import lombok.Builder;
import lombok.Data;
import org.bson.types.ObjectId;

@Data
@Builder
public class EvaStreamRequest {

    private String message;
    private String system;
    private String granularity;
    private int days;
    private int hours;
    private String tz;
    private ObjectId tenantId;
    private String actorName;

}
