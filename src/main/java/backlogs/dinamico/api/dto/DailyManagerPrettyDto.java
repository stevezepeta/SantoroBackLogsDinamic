package backlogs.dinamico.api.dto;

import backlogs.dinamico.service.ai.dto.DailyManagerSummaryDto;

import java.util.List;

public class DailyManagerPrettyDto {

    public DailyManagerSummaryDto base;
    public PrettyManager pretty;

    public static class PrettyManager {
        public String executiveNarrative;
        public List<String> executiveBullets;
        public List<PrettyDraft> draftEnhancements;
    }

    public static class PrettyDraft {
        // Debe coincidir EXACTO con el draft base para poder mapear
        public String title;

        public String oneLineSummary;
        public List<String> likelyCauses;
        public String impact;
        public List<String> nextSteps;

        // 0..1
        public double confidence;

        // “No hay samples”, “Faltan campos”, etc.
        public List<String> caveats;
    }

}
