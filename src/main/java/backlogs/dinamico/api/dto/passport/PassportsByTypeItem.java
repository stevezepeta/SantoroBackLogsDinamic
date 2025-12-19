package backlogs.dinamico.api.dto.passport;

public record PassportsByTypeItem(

        String tramiteType,
        long emitidos,
        long enTramite,
        long rechazados,
        long cancelados

) {
}
