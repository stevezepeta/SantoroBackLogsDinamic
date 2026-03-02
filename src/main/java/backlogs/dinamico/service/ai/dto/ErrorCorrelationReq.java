package backlogs.dinamico.service.ai.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class ErrorCorrelationReq {

    public String tz;

    public Instant from;
    public Instant to;

    public String system;
    public List<String> errors;

    public Integer samplesPerCluster;

    public Map<String, Object> meta;

}
