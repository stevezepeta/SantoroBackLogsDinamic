package backlogs.dinamico.service.ai.dto;

import java.util.List;
import java.util.Map;

public class TicketDraftDto {

    public String tz;
    public String alertId;
    public String title;
    public String priority;
    public List<String> labels;
    public String format; 
    public String descriptionMarKdown;
    public List<String> stepsToReproduce;
    public String expectedBehavior;
    public String actualBehavior;

    public Map<String, Object> suggestedFilters;
    public Map<String, Object> meta;

}
