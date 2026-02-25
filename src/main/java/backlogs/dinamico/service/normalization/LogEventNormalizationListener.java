package backlogs.dinamico.service.normalization;

import backlogs.dinamico.model.log.LogEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertEvent;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LogEventNormalizationListener extends AbstractMongoEventListener<LogEvent> {

    private final backlogs.dinamico.service.normalization.SeverityNormalizer severityNormalizer;

    @Override
    public void onBeforeConvert(BeforeConvertEvent<LogEvent> event) {

        LogEvent e = event.getSource();
        if (e == null) return;

        if (isBlank(e.getSeverity())) {
            e.setSeverityRaw(e.getSeverity());
        }

        String normalized = severityNormalizer.normalize(e);
        e.setSeverity(normalized);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

}
