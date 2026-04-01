package backlogs.dinamico.model.passport;

import backlogs.dinamico.model.catalog.Office;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Document("passport_events")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PassportEvent {

    @Id
    private ObjectId id;

    private ObjectId tenantId;

    private String system;
    private String caseId;
    private String operationType;
    private String status;
    private Instant eventTime;

    private String message;

    private PassportInfo passport;
    private OfficeInfo office;
    private String channel;
    private UserInfo user;
    private SlaInfo sla;
    private ReasonInfo reason;
    private Map<String, Object> meta;


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PassportInfo{
        private String passportNumber;
        private String personId;
        private String fullName;
        private String nationality;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OfficeInfo {
        private String officeId;
        private String officeName;
        private String province;
        private String city;
        private GeoPointInfo geoPoint;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GeoPointInfo {
        private String type;
        private List<Double> coordinates;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UserInfo {
        private String userId;
        private String username;
        private String fullName;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SlaInfo {
        private Instant startTime;
        private Instant endTime;
        private Long elapsedSeconds;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ReasonInfo {
        private String code;
        private String description;
    }

}
