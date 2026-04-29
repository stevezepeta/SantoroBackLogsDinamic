package backlogs.dinamico.service.email;

import backlogs.dinamico.model.ai.AiMetricRecord;
import backlogs.dinamico.repository.ai.AiMetricRepository;
import backlogs.dinamico.service.ai.EvaDeepAnalysisService;
import backlogs.dinamico.service.ai.dto.SummaryInsightsDto;
import com.lowagie.text.DocumentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertReportPdfService {

    private final AiMetricRepository     metricRepo;
    private final EvaDeepAnalysisService deepAnalysis;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                    .withZone(ZoneId.of("America/Mexico_City"));
    private static final DateTimeFormatter DAY_FMT =
            DateTimeFormatter.ofPattern("dd/MM")
                    .withZone(ZoneId.of("America/Mexico_City"));

    public byte[] generateReport(
            ObjectId tenantId,
            String system,
            SummaryInsightsDto out,
            EvaDeepAnalysisService.DeepAnalysisResult aiAnalysis
    ) {
        try {
            String html = buildHtml(tenantId, system, out, aiAnalysis);
            return renderPdf(html);
        } catch (Exception e) {
            log.error("[AlertPdf] Error generando PDF para {}: {}", system, e.getMessage());
            return null;
        }
    }

    private String buildHtml(
            ObjectId tenantId,
            String system,
            SummaryInsightsDto out,
            EvaDeepAnalysisService.DeepAnalysisResult ai
    ) {
        // ── Status ────────────────────────────────────────────────────────────
        boolean isCrit     = "CRIT".equalsIgnoreCase(out.status);
        String statusColor = isCrit ? "#dc2626" : "#d97706";
        String statusBg    = isCrit ? "#fef2f2" : "#fffbeb";
        String statusLabel = isCrit ? "CRITICO" : "ADVERTENCIA";

        // ── Pre-convertir TODO a String — sin double/long en .formatted() ─────
        String strNow        = FMT.format(Instant.now());
        String strSystem     = system != null ? escHtml(system) : "-";

        DateTimeFormatter readFmt  = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        DateTimeFormatter writeFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        String strFrom = formatPeriodDate(out.fromLocal != null ? out.fromLocal : out.from);
        String strTo   = formatPeriodDate(out.toLocal   != null ? out.toLocal   : out.to);

        String strTotal      = String.valueOf(out.total);
        String strErrorRate  = String.format(Locale.ROOT, "%.1f%%", out.errorRate * 100);
        long   failuresLong  = (out.severities != null)
                ? out.severities.getOrDefault("ERROR", 0L) : 0L;
        String strFailures   = String.valueOf(failuresLong);
        String strSuccesses  = String.valueOf(out.total - failuresLong);

        String strAiEvent    = (ai != null && ai.dominantEventType() != null)
                ? escHtml(ai.dominantEventType()) + " (" + ai.dominantCount() + " ocurrencias)"
                : "-";
        String strAiSummary  = (ai != null && ai.aiSummary() != null)
                ? escHtml(ai.aiSummary()) : "No disponible.";

        String barChart          = buildBarChart(tenantId, system);
        String aiSuggestionsHtml = buildSuggestionsHtml(ai);
        String warningsHtml      = buildWarningsHtml(out);
        String topErrorsHtml     = buildTopErrorsHtml(out);
        // ─────────────────────────────────────────────────────────────────────

        // IMPORTANTE: solo %s en el template, sin %.1f ni %d
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\"\n"
                + "    \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n"
                + "<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"es\">\n"
                + "<head>\n"
                + "  <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/>\n"
                + "  <style type=\"text/css\">\n"
                + "    * { margin:0; padding:0; }\n"
                + "    body { font-family:Arial,Helvetica,sans-serif; font-size:13px;\n"
                + "           color:#1f2937; background:#ffffff; }\n"
                + "    .header { background:#111827; padding:22px 36px 18px 36px;\n"
                + "              border-bottom:4px solid " + statusColor + "; }\n"
                + "    .header-brand { font-size:24px; font-weight:bold;\n"
                + "                   color:#ffffff; letter-spacing:2px; }\n"
                + "    .header-sub { font-size:9px; color:#6b7280;\n"
                + "                  letter-spacing:2px; margin-top:4px; }\n"
                + "    .header-date { font-size:10px; color:#9ca3af; margin-top:8px; }\n"
                + "    .status-banner { background:" + statusBg + ";\n"
                + "                     border-left:6px solid " + statusColor + ";\n"
                + "                     padding:16px 36px; }\n"
                + "    .status-text { font-size:20px; font-weight:bold;\n"
                + "                   color:" + statusColor + "; }\n"
                + "    .status-system { font-size:13px; color:#374151; margin-top:6px; }\n"
                + "    .section { padding:20px 36px; border-bottom:1px solid #f3f4f6; }\n"
                + "    .section-title { font-size:10px; font-weight:bold; color:#6b7280;\n"
                + "                     text-transform:uppercase; letter-spacing:1px;\n"
                + "                     margin-bottom:14px; padding-bottom:6px;\n"
                + "                     border-bottom:2px solid #f3f4f6; }\n"
                + "    .ai-box { background:#f5f3ff; border:1px solid #ddd6fe;\n"
                + "              border-left:4px solid #7c3aed;\n"
                + "              padding:14px 16px; margin-top:6px; }\n"
                + "    .ai-label { font-size:9px; font-weight:bold; color:#7c3aed;\n"
                + "                text-transform:uppercase; letter-spacing:1px;\n"
                + "                margin-bottom:6px; }\n"
                + "    .ai-event { background:#4338ca; color:#ffffff; font-size:10px;\n"
                + "                font-weight:bold; padding:4px 10px;\n"
                + "                margin-bottom:10px; display:block; }\n"
                + "    .ai-text { font-size:12px; line-height:1.7; color:#374151; }\n"
                + "    .warn-box { background:#fefce8; border:1px solid #fde68a;\n"
                + "                padding:12px 16px; }\n"
                + "    .footer { background:#f9fafb; padding:14px 36px;\n"
                + "              border-top:1px solid #e5e7eb; margin-top:20px; }\n"
                + "    .footer-text { font-size:9px; color:#9ca3af; }\n"
                + "  </style>\n"
                + "</head>\n"
                + "<body>\n"

                // HEADER
                + "  <div class=\"header\">\n"
                + "    <div class=\"header-brand\">DataLogs</div>\n"
                + "    <div class=\"header-sub\">SISTEMA DE GESTION DE LOGS - REPORTE DE ALERTA</div>\n"
                + "    <div class=\"header-date\">Generado: " + strNow + "</div>\n"
                + "  </div>\n"

                // BANNER
                + "  <div class=\"status-banner\">\n"
                + "    <div class=\"status-text\">" + statusLabel + "</div>\n"
                + "    <div class=\"status-system\">Sistema: <strong>" + strSystem + "</strong>"
                + "    &nbsp;|&nbsp; Periodo: " + strFrom + " &#8594; " + strTo + "</div>\n"
                + "  </div>\n"

                // MÉTRICAS
                + "  <div class=\"section\">\n"
                + "    <div class=\"section-title\">Metricas del periodo</div>\n"
                + "    <table width=\"100%\" cellspacing=\"8\" cellpadding=\"0\">\n"
                + "      <tr>\n"
                + "        <td style=\"background:#f9fafb;border:1px solid #e5e7eb;"
                + "padding:14px 8px;text-align:center;width:25%;\">\n"
                + "          <div style=\"font-size:9px;color:#9ca3af;text-transform:uppercase;"
                + "letter-spacing:1px;\">Total eventos</div>\n"
                + "          <div style=\"font-size:24px;font-weight:bold;margin-top:6px;"
                + "color:#111827;\">" + strTotal + "</div>\n"
                + "        </td>\n"
                + "        <td style=\"background:#f9fafb;border:1px solid #e5e7eb;"
                + "padding:14px 8px;text-align:center;width:25%;\">\n"
                + "          <div style=\"font-size:9px;color:#9ca3af;text-transform:uppercase;"
                + "letter-spacing:1px;\">Error rate</div>\n"
                + "          <div style=\"font-size:24px;font-weight:bold;margin-top:6px;"
                + "color:" + statusColor + ";\">" + strErrorRate + "</div>\n"
                + "        </td>\n"
                + "        <td style=\"background:#f9fafb;border:1px solid #e5e7eb;"
                + "padding:14px 8px;text-align:center;width:25%;\">\n"
                + "          <div style=\"font-size:9px;color:#9ca3af;text-transform:uppercase;"
                + "letter-spacing:1px;\">Failures</div>\n"
                + "          <div style=\"font-size:24px;font-weight:bold;margin-top:6px;"
                + "color:#dc2626;\">" + strFailures + "</div>\n"
                + "        </td>\n"
                + "        <td style=\"background:#f9fafb;border:1px solid #e5e7eb;"
                + "padding:14px 8px;text-align:center;width:25%;\">\n"
                + "          <div style=\"font-size:9px;color:#9ca3af;text-transform:uppercase;"
                + "letter-spacing:1px;\">Successes</div>\n"
                + "          <div style=\"font-size:24px;font-weight:bold;margin-top:6px;"
                + "color:#16a34a;\">" + strSuccesses + "</div>\n"
                + "        </td>\n"
                + "      </tr>\n"
                + "    </table>\n"
                + "  </div>\n"

                // GRÁFICA
                + "  <div class=\"section\">\n"
                + "    <div class=\"section-title\">Tendencia error rate - ultimos 7 dias</div>\n"
                + barChart + "\n"
                + "  </div>\n"

                // ANÁLISIS IA
                + "  <div class=\"section\">\n"
                + "    <div class=\"section-title\">Analisis generado por sistema</div>\n"
                + "    <div class=\"ai-box\">\n"
                + "      <div class=\"ai-label\">Evento dominante</div>\n"
                + "      <div class=\"ai-event\">" + strAiEvent + "</div>\n"
                + "      <div class=\"ai-label\" style=\"margin-top:8px;\">Que esta pasando</div>\n"
                + "      <div class=\"ai-text\">" + strAiSummary + "</div>\n"
                + "    </div>\n"
                + "  </div>\n"

                // ACCIONES
                + "  <div class=\"section\">\n"
                + "    <div class=\"section-title\">Acciones especificas sugeridas</div>\n"
                + aiSuggestionsHtml + "\n"
                + "  </div>\n"

                // ALERTAS
                + "  <div class=\"section\">\n"
                + "    <div class=\"section-title\">Alertas detectadas</div>\n"
                + "    <div class=\"warn-box\">" + warningsHtml + "</div>\n"
                + "  </div>\n"

                // TOP ERRORES
                + "  <div class=\"section\">\n"
                + "    <div class=\"section-title\">Errores mas frecuentes</div>\n"
                + topErrorsHtml + "\n"
                + "  </div>\n"

                // FOOTER
                + "  <div class=\"footer\">\n"
                + "    <div class=\"footer-text\">\n"
                + "      DataLogs - Grupo Santoro - soporte.tecnico@grupo-santoro.com.mx\n"
                + "      - Reporte generado automaticamente. No responder a este correo.\n"
                + "    </div>\n"
                + "  </div>\n"

                + "</body>\n"
                + "</html>";
    }

    private String buildBarChart(ObjectId tenantId, String system) {
        List<AiMetricRecord> records = metricRepo
                .findTop120ByTenantIdAndGranularityAndSystemOrderByBucketStartDesc(
                        tenantId, "daily", system);

        if (records == null || records.isEmpty()) {
            return "<p style='color:#9ca3af;font-size:11px;margin:0'>"
                    + "Sin datos historicos disponibles.</p>";
        }

        List<AiMetricRecord> last7 = records.stream()
                .sorted(java.util.Comparator.comparing(AiMetricRecord::getBucketStart))
                .skip(Math.max(0, records.size() - 7))
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"4\" "
                + "style=\"background:#f9fafb;border:1px solid #e5e7eb;padding:12px;\">");
        sb.append("<tr valign=\"bottom\" style=\"height:120px;\">");

        for (AiMetricRecord rec : last7) {
            double rate     = Math.min(rec.getErrorRate(), 1.0);
            int    heightPx = Math.max((int)(100 * rate), 2);
            String color    = rate >= 0.15 ? "#dc2626"
                    : rate >= 0.05 ? "#d97706" : "#16a34a";
            String pctLbl   = String.format(Locale.ROOT, "%.0f%%", rate * 100);
            String dayLbl   = rec.getBucketStart() != null
                    ? DAY_FMT.format(rec.getBucketStart()) : "";

            sb.append("<td align=\"center\" valign=\"bottom\" "
                    + "style=\"padding:0 4px;width:14%;\">");
            sb.append("<div style=\"font-size:8px;color:" + color
                    + ";font-weight:bold;margin-bottom:2px;\">" + pctLbl + "</div>");
            sb.append("<div style=\"height:" + heightPx + "px;background:" + color
                    + ";border-top-left-radius:3px;border-top-right-radius:3px;\"></div>");
            sb.append("<div style=\"font-size:8px;color:#6b7280;margin-top:4px;\">"
                    + dayLbl + "</div>");
            sb.append("</td>");
        }
        sb.append("</tr>");

        sb.append("<tr><td colspan=\"7\" style=\"padding:8px 4px 4px;\">");
        sb.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr>");
        sb.append("<td style=\"font-size:8px;color:#374151;padding:0 8px 0 0;\">"
                + "<table cellpadding=\"0\" cellspacing=\"0\"><tr>"
                + "<td style=\"width:10px;height:10px;background:#16a34a;\"></td>"
                + "<td style=\"padding-left:4px;\">Normal (&lt;5%)</td>"
                + "</tr></table></td>");
        sb.append("<td style=\"font-size:8px;color:#374151;padding:0 8px 0 0;\">"
                + "<table cellpadding=\"0\" cellspacing=\"0\"><tr>"
                + "<td style=\"width:10px;height:10px;background:#d97706;\"></td>"
                + "<td style=\"padding-left:4px;\">Advertencia (5-15%)</td>"
                + "</tr></table></td>");
        sb.append("<td style=\"font-size:8px;color:#374151;\">"
                + "<table cellpadding=\"0\" cellspacing=\"0\"><tr>"
                + "<td style=\"width:10px;height:10px;background:#dc2626;\"></td>"
                + "<td style=\"padding-left:4px;\">Critico (&gt;15%)</td>"
                + "</tr></table></td>");
        sb.append("</tr></table></td></tr></table>");

        return sb.toString();
    }

    private String buildSuggestionsHtml(EvaDeepAnalysisService.DeepAnalysisResult ai) {
        if (ai == null || ai.aiSuggestions() == null) {
            return "<p style=\"color:#9ca3af;font-size:11px\">No disponible.</p>";
        }
        StringBuilder sb = new StringBuilder();
        for (String line : ai.aiSuggestions().split("\n")) {
            String l = line.trim();
            if (l.isBlank()) continue;
            sb.append("<div style=\"padding:6px 0 6px 12px;border-left:3px solid #7c3aed;"
                    + "margin-bottom:6px;font-size:11px;color:#374151\">"
                    + escHtml(l) + "</div>");
        }
        return sb.toString();
    }

    private String buildWarningsHtml(SummaryInsightsDto out) {
        if (out.warnings == null || out.warnings.isEmpty()) return "Sin advertencias.";
        return out.warnings.stream()
                .map(w -> "<div style=\"font-size:11px;color:#92400e;padding:3px 0\">- "
                        + escHtml(w) + "</div>")
                .collect(Collectors.joining());
    }

    private String buildTopErrorsHtml(SummaryInsightsDto out) {
        if (out.topErrorsRange == null || out.topErrorsRange.isEmpty()) {
            return "<p style=\"color:#9ca3af;font-size:11px\">"
                    + "Sin errores frecuentes registrados.</p>";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<table style=\"width:100%;border-collapse:collapse;\">");
        sb.append("<tr style=\"background:#f9fafb;\">");
        sb.append("<th style=\"padding:6px 8px;text-align:left;font-size:9px;color:#6b7280;"
                + "text-transform:uppercase;border-bottom:1px solid #e5e7eb;\">#</th>");
        sb.append("<th style=\"padding:6px 8px;text-align:left;font-size:9px;color:#6b7280;"
                + "text-transform:uppercase;border-bottom:1px solid #e5e7eb;\">Error</th>");
        sb.append("<th style=\"padding:6px 8px;text-align:right;font-size:9px;color:#6b7280;"
                + "text-transform:uppercase;border-bottom:1px solid #e5e7eb;\">Ocurrencias</th>");
        sb.append("</tr>");

        int i = 1;
        for (SummaryInsightsDto.TopError err : out.topErrorsRange) {
            if (err == null) continue;
            String key = err.key != null
                    ? (err.key.length() > 90 ? err.key.substring(0, 90) + "..." : err.key) : "-";
            String count = String.valueOf(err.count);
            sb.append("<tr style=\"border-bottom:1px solid #f3f4f6;\">"
                    + "<td style=\"padding:5px 8px;font-size:10px;color:#6b7280;\">" + i + "</td>"
                    + "<td style=\"padding:5px 8px;font-size:10px;color:#374151;\">"
                    + escHtml(key) + "</td>"
                    + "<td style=\"padding:5px 8px;font-size:10px;font-weight:bold;"
                    + "text-align:right;color:#dc2626;\">" + count + "</td>"
                    + "</tr>");
            i++;
        }
        sb.append("</table>");
        return sb.toString();
    }

    private String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private byte[] renderPdf(String html) throws DocumentException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ITextRenderer renderer = new ITextRenderer();
        renderer.setDocumentFromString(html);
        renderer.layout();
        renderer.createPDF(baos);
        return baos.toByteArray();
    }

    private String formatPeriodDate(String isoDate) {
        if (isoDate == null) return "-";
        try {
            java.time.OffsetDateTime odt = java.time.OffsetDateTime.parse(isoDate);
            return odt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        } catch (Exception e) {
            try {
                Instant inst = Instant.parse(isoDate);
                return FMT.format(inst); // FMT ya está definido en la clase
            } catch (Exception ex) {
                return isoDate;
            }
        }
    }
}