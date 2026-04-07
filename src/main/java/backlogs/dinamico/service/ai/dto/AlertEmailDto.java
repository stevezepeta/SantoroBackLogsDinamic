package backlogs.dinamico.service.ai.dto;

import java.util.List;

public record AlertEmailDto(
        String  status,        // "CRIT" | "WARN"
        String  granularity,   // "hourly" | "daily"
        String  fromLocal,     // "05/04/2026 00:00"
        String  toLocal,       // "06/04/2026 00:00"
        long    total,
        double  errorRate,
        String  topSystem,     // sistema más afectado
        long    topSystemCount,
        String  topError,      // error más frecuente
        List<String> warnings  // lista de warnings
) {}
