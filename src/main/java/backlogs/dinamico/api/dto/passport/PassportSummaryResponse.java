package backlogs.dinamico.api.dto.passport;

import java.time.Instant;
import java.util.List;

public record PassportSummaryResponse(

        Instant from,
        Instant to,
        Totales totales,
        List<PorDia> porDia

) {

    // Totales globales del periodo
    public record Totales(
            long emitidos,
            long enTramite,
            long rechazados,
            long cancelados
    ) {}

    // Agregación por día
    public record PorDia(
            String fecha,   // "2025-12-10"
            long emitidos,
            long enTramite,
            long rechazados,
            long cancelados
    ) {}
}
