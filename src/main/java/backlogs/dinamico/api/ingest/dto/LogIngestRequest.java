package backlogs.dinamico.api.ingest.dto;

import lombok.Data;
import org.bson.Document;

import java.time.Instant;

@Data
public class LogIngestRequest {
  private Instant eventAt;
  private String severity;      
  private String traceId;
  private String spanId;
  private String eventTypeCode;
  private String errorCode;     
  private String sessionToken;  
  private String deviceCode;   
  private String officeId;      
  private Document source;      
  private Document payload;    
}
