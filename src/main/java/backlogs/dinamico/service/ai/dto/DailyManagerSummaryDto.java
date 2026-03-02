package backlogs.dinamico.service.ai.dto;

import java.util.List;
import java.util.Map;

public class DailyManagerSummaryDto {

    public String tz;

    public String from;

    public String to;

    public String fromLocal;

    public String toLocal;

    public int days;

    public long total;

    public double errorRate;

    public String status;

    public Map<String, Long> severities;

    public List<SummaryInsightsDto.TopItem> topSystemsRange;
    public List<SummaryInsightsDto.TopItem> topEventTypesRange;
    public List<SummaryInsightsDto.TopItem> topStatusRange;
    public List<SummaryInsightsDto.TopItem> topOutcomeRange;
    public List<SummaryInsightsDto.TopError> topErrorsRange;

    // bullets "para gerente"
    public List<String> executiveSummary;
    public List<String> risks;
    public List<String> actions;

    public Map<String, Object> suggestedFilters;


}
