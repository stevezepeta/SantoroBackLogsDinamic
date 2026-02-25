package backlogs.dinamico.service.logs;

import backlogs.dinamico.model.log.LogEvent;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LogEventNormalizationCallback implements BeforeConvertCallback<LogEvent> {

    @Override
    public LogEvent onBeforeConvert(LogEvent e, String collection) {

        // SeverityRaw
        if (!StringUtils.hasText(e.getSeverityRaw())) {
            e.setSeverityRaw(e.getSeverity());
        }

        // severity normalizado
        String sevNorm = LogNormalizationUtils.normalizeSeverity(e.getSeverityRaw());
        if (StringUtils.hasText(sevNorm)) {
            e.setSeverity(sevNorm);
        }

        // messageKey (message o reason.description)
        String reasonDesc = (e.getReason() != null) ? e.getReason().getDescription() : null;
        if (!StringUtils.hasText(e.getMessage())) {
            e.setMessageKey(LogNormalizationUtils.buildMessageKey(e.getMessage(), reasonDesc));
        }

        // isError
        if (e.getIsError() == null) {
            e.setIsError(LogNormalizationUtils.computeIsError(e.getSeverity(), e.getStatus(), e.getOutcome()));
        }

        return e;
    }


}
