package backlogs.dinamico.model.ai;

import lombok.Data;
import java.util.List;

@Data
public class WeeklyReportData {

    // ── Etiquetas de semana ───────────────────────────────────────────────────
    public String weekLabel;       // "07/04/2026 — 13/04/2026"
    public String prevWeekLabel;   // "31/03/2026 — 06/04/2026"

    // ── Totales comparativos ──────────────────────────────────────────────────
    public long   totalThisWeek;
    public long   totalLastWeek;
    public double totalChangePct;  // positivo = subió, negativo = bajó

    // ── Error rate comparativo ────────────────────────────────────────────────
    public double errorRateThisWeek;
    public double errorRateLastWeek;
    public double errorRateChangePct;

    // ── Sistema más activo ────────────────────────────────────────────────────
    public String topSystem;
    public long   topSystemTotal;

    // ── Por sistema ───────────────────────────────────────────────────────────
    public List<SystemWeeklyStats> systems;

    // ── Top errores de la semana ──────────────────────────────────────────────
    public List<TopError> topErrors;

    // ── Análisis IA ───────────────────────────────────────────────────────────
    public String aiSummary;
    public String aiSuggestions;

    // ── Estado general ────────────────────────────────────────────────────────
    public String status; // "HEALTHY", "WARN", "CRIT"

    // ── Inner classes ─────────────────────────────────────────────────────────

    @Data
    public static class SystemWeeklyStats {
        public String system;
        public long   totalThisWeek;
        public long   totalLastWeek;
        public double errorRateThisWeek;
        public double errorRateLastWeek;
        public long   failuresThisWeek;
        public long   successesThisWeek;
        public String trend; // "UP", "DOWN", "STABLE"
    }

    @Data
    public static class TopError {
        public String key;
        public long   count;
    }
}