package backlogs.dinamico.api.dto.passport;

public record PassportsByOfficeItem(

        String officeId,
        String officeName,
        long emitidos,
        long enTramite,
        long rechazados,
        long cancelados

) {
}
