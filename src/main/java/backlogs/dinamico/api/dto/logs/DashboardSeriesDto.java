package backlogs.dinamico.api.dto.logs;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DashboardSeriesDto {

    private List<TimePoint> byDay;

    private List<TimePoint> byWeek;

    private List<TimePoint> byMonth;

    private List<StatusTimePoint> statusOverTime;


    @Data
    @Builder
    public static class TimePoint {
        private String date;
        private long count;
    }

    @Data
    @Builder
    public static class StatusTimePoint {
        private String date;
        private String status;
        private long count;
    }

}
