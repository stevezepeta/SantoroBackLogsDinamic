package backlogs.dinamico.service.email;

import backlogs.dinamico.model.ai.WeeklyReportData;
import com.lowagie.text.DocumentException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class WeeklyReportPdfService {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                    .withZone(ZoneId.of("America/Mexico_City"));

    public byte[] generate(WeeklyReportData data) {
        try {
            String html = buildHtml(data);
            return renderPdf(html);
        } catch (Exception e) {
            log.error("[WeeklyPdf] Error generando PDF: {}", e.getMessage());
            return null;
        }
    }

    private String buildHtml(WeeklyReportData data) {
        // ── Pre-convertir todos los valores ──────────────────────────────────
        String strNow          = FMT.format(Instant.now());
        String strWeekLabel    = esc(data.weekLabel);
        String strPrevLabel    = esc(data.prevWeekLabel);
        String strTotal        = String.valueOf(data.totalThisWeek);
        String strTotalPrev    = String.valueOf(data.totalLastWeek);
        String strTotalChg     = formatChange(data.totalChangePct);
        String strErrorRate    = String.format(Locale.ROOT, "%.1f%%", data.errorRateThisWeek * 100);
        String strErrorRatePrev= String.format(Locale.ROOT, "%.1f%%", data.errorRateLastWeek * 100);
        String strErrorChg     = formatChange(data.errorRateChangePct);
        String strTopSystem    = esc(data.topSystem != null ? data.topSystem : "-");
        String strTopSysTotal  = String.valueOf(data.topSystemTotal);
        String statusColor     = statusColor(data.status);
        String statusBg        = statusBg(data.status);
        String statusLabel     = statusLabel(data.status);
        String strAiSummary    = esc(data.aiSummary != null ? data.aiSummary : "No disponible.");
        String systemsTable    = buildSystemsTable(data.systems);
        String topErrorsTable  = buildTopErrorsTable(data.topErrors);
        String suggestionsHtml = buildSuggestions(data.aiSuggestions);

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
                + "              border-bottom:4px solid #7c3aed; }\n"
                + "    .header-brand { font-size:24px; font-weight:bold;\n"
                + "                    color:#ffffff; letter-spacing:2px; }\n"
                + "    .header-sub { font-size:9px; color:#6b7280;\n"
                + "                  letter-spacing:2px; margin-top:4px; }\n"
                + "    .header-date { font-size:10px; color:#9ca3af; margin-top:8px; }\n"
                + "    .status-banner { background:" + statusBg + ";\n"
                + "                     border-left:6px solid " + statusColor + ";\n"
                + "                     padding:16px 36px; }\n"
                + "    .status-text { font-size:20px; font-weight:bold;\n"
                + "                   color:" + statusColor + "; }\n"
                + "    .status-period { font-size:12px; color:#374151; margin-top:4px; }\n"
                + "    .section { padding:20px 36px; border-bottom:1px solid #f3f4f6; }\n"
                + "    .section-title { font-size:10px; font-weight:bold; color:#6b7280;\n"
                + "                     text-transform:uppercase; letter-spacing:1px;\n"
                + "                     margin-bottom:14px; padding-bottom:6px;\n"
                + "                     border-bottom:2px solid #f3f4f6; }\n"
                + "    .kpi-box { background:#f9fafb; border:1px solid #e5e7eb;\n"
                + "               padding:14px 8px; text-align:center; }\n"
                + "    .kpi-label { font-size:9px; color:#9ca3af;\n"
                + "                 text-transform:uppercase; letter-spacing:1px; }\n"
                + "    .kpi-value { font-size:24px; font-weight:bold;\n"
                + "                 margin-top:6px; color:#111827; }\n"
                + "    .kpi-prev { font-size:10px; color:#6b7280; margin-top:4px; }\n"
                + "    .kpi-change-up   { color:#dc2626; font-weight:bold; }\n"
                + "    .kpi-change-down { color:#16a34a; font-weight:bold; }\n"
                + "    .kpi-change-flat { color:#6b7280; }\n"
                + "    .data-table { width:100%; border-collapse:collapse; font-size:11px; }\n"
                + "    .data-table th { background:#f3f4f6; padding:6px 8px;\n"
                + "                     text-align:left; color:#6b7280;\n"
                + "                     text-transform:uppercase; font-size:9px;\n"
                + "                     border-bottom:1px solid #e5e7eb; }\n"
                + "    .data-table td { padding:6px 8px;\n"
                + "                     border-bottom:1px solid #f3f4f6; color:#374151; }\n"
                + "    .trend-up   { color:#dc2626; font-weight:bold; }\n"
                + "    .trend-down { color:#16a34a; font-weight:bold; }\n"
                + "    .trend-flat { color:#6b7280; }\n"
                + "    .ai-box { background:#f5f3ff; border:1px solid #ddd6fe;\n"
                + "              border-left:4px solid #7c3aed;\n"
                + "              padding:14px 16px; margin-top:6px; }\n"
                + "    .ai-label { font-size:9px; font-weight:bold; color:#7c3aed;\n"
                + "                text-transform:uppercase; letter-spacing:1px;\n"
                + "                margin-bottom:6px; }\n"
                + "    .ai-text { font-size:12px; line-height:1.7; color:#374151; }\n"
                + "    .footer { background:#f9fafb; padding:14px 36px;\n"
                + "              border-top:1px solid #e5e7eb; margin-top:20px; }\n"
                + "    .footer-text { font-size:9px; color:#9ca3af; }\n"
                + "  </style>\n"
                + "</head>\n"
                + "<body>\n"

                // HEADER
                + "  <div class=\"header\">\n"
                + "    <div class=\"header-brand\">DataLogs</div>\n"
                + "    <div class=\"header-sub\">SISTEMA DE GESTION DE LOGS - RESUMEN EJECUTIVO SEMANAL</div>\n"
                + "    <div class=\"header-date\">Generado: " + strNow + "</div>\n"
                + "  </div>\n"

                // BANNER
                + "  <div class=\"status-banner\">\n"
                + "    <div class=\"status-text\">" + statusLabel + " - Estado de la semana</div>\n"
                + "    <div class=\"status-period\">Semana: <strong>" + strWeekLabel + "</strong>"
                + "    &nbsp;|&nbsp; Semana anterior: " + strPrevLabel + "</div>\n"
                + "  </div>\n"

                // KPIs
                + "  <div class=\"section\">\n"
                + "    <div class=\"section-title\">Resumen comparativo</div>\n"
                + "    <table width=\"100%\" cellspacing=\"8\" cellpadding=\"0\">\n"
                + "      <tr>\n"
                + "        <td class=\"kpi-box\" style=\"width:25%;\">\n"
                + "          <div class=\"kpi-label\">Total eventos</div>\n"
                + "          <div class=\"kpi-value\">" + strTotal + "</div>\n"
                + "          <div class=\"kpi-prev\">Anterior: " + strTotalPrev + "</div>\n"
                + "          <div class=\"" + changeClass(data.totalChangePct) + "\">"
                +               strTotalChg + "</div>\n"
                + "        </td>\n"
                + "        <td class=\"kpi-box\" style=\"width:25%;\">\n"
                + "          <div class=\"kpi-label\">Error rate</div>\n"
                + "          <div class=\"kpi-value\" style=\"color:" + statusColor + ";\">"
                +               strErrorRate + "</div>\n"
                + "          <div class=\"kpi-prev\">Anterior: " + strErrorRatePrev + "</div>\n"
                + "          <div class=\"" + changeClass(data.errorRateChangePct) + "\">"
                +               strErrorChg + "</div>\n"
                + "        </td>\n"
                + "        <td class=\"kpi-box\" style=\"width:25%;\">\n"
                + "          <div class=\"kpi-label\">Sistema mas activo</div>\n"
                + "          <div class=\"kpi-value\" style=\"font-size:14px;\">"
                +               strTopSystem + "</div>\n"
                + "          <div class=\"kpi-prev\">" + strTopSysTotal + " eventos</div>\n"
                + "        </td>\n"
                + "        <td class=\"kpi-box\" style=\"width:25%;\">\n"
                + "          <div class=\"kpi-label\">Estado general</div>\n"
                + "          <div class=\"kpi-value\" style=\"color:" + statusColor + ";font-size:16px;\">"
                +               statusLabel + "</div>\n"
                + "        </td>\n"
                + "      </tr>\n"
                + "    </table>\n"
                + "  </div>\n"

                // POR SISTEMA
                + "  <div class=\"section\">\n"
                + "    <div class=\"section-title\">Rendimiento por sistema</div>\n"
                + systemsTable
                + "  </div>\n"

                // ANÁLISIS IA
                + "  <div class=\"section\">\n"
                + "    <div class=\"section-title\">Analisis ejecutivo generado por IA</div>\n"
                + "    <div class=\"ai-box\">\n"
                + "      <div class=\"ai-label\">Que paso esta semana</div>\n"
                + "      <div class=\"ai-text\">" + strAiSummary + "</div>\n"
                + "    </div>\n"
                + "  </div>\n"

                // ACCIONES
                + "  <div class=\"section\">\n"
                + "    <div class=\"section-title\">Acciones recomendadas</div>\n"
                + suggestionsHtml
                + "  </div>\n"

                // TOP ERRORES
                + "  <div class=\"section\">\n"
                + "    <div class=\"section-title\">Errores mas frecuentes de la semana</div>\n"
                + topErrorsTable
                + "  </div>\n"

                // FOOTER
                + "  <div class=\"footer\">\n"
                + "    <div class=\"footer-text\">\n"
                + "      DataLogs - Grupo Santoro - soporte.tecnico@grupo-santoro.com.mx\n"
                + "      - Reporte semanal generado automaticamente. No responder a este correo.\n"
                + "    </div>\n"
                + "  </div>\n"
                + "</body>\n"
                + "</html>";
    }

    private String buildSystemsTable(List<WeeklyReportData.SystemWeeklyStats> systems) {
        if (systems == null || systems.isEmpty()) {
            return "<p style=\"color:#9ca3af;font-size:11px\">Sin datos de sistemas.</p>";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<table class=\"data-table\">");
        sb.append("<tr>");
        sb.append("<th>Sistema</th>");
        sb.append("<th>Total (semana)</th>");
        sb.append("<th>Total (anterior)</th>");
        sb.append("<th>Error rate</th>");
        sb.append("<th>Error rate ant.</th>");
        sb.append("<th>Failures</th>");
        sb.append("<th>Successes</th>");
        sb.append("<th>Tendencia</th>");
        sb.append("</tr>");

        for (WeeklyReportData.SystemWeeklyStats s : systems) {
            String trendLabel = trendLabel(s.getTrend());
            String trendClass = trendClass(s.getTrend());
            sb.append("<tr>");
            sb.append("<td style=\"font-weight:bold;\">").append(esc(s.getSystem())).append("</td>");
            sb.append("<td>").append(s.getTotalThisWeek()).append("</td>");
            sb.append("<td style=\"color:#6b7280;\">").append(s.getTotalLastWeek()).append("</td>");
            sb.append("<td style=\"font-weight:bold;\">")
                    .append(String.format(Locale.ROOT, "%.1f%%", s.getErrorRateThisWeek() * 100))
                    .append("</td>");
            sb.append("<td style=\"color:#6b7280;\">")
                    .append(String.format(Locale.ROOT, "%.1f%%", s.getErrorRateLastWeek() * 100))
                    .append("</td>");
            sb.append("<td style=\"color:#dc2626;\">").append(s.getFailuresThisWeek()).append("</td>");
            sb.append("<td style=\"color:#16a34a;\">").append(s.getSuccessesThisWeek()).append("</td>");
            sb.append("<td class=\"").append(trendClass).append("\">").append(trendLabel).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }

    private String buildTopErrorsTable(List<WeeklyReportData.TopError> errors) {
        if (errors == null || errors.isEmpty()) {
            return "<p style=\"color:#9ca3af;font-size:11px\">Sin errores frecuentes registrados.</p>";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<table class=\"data-table\">");
        sb.append("<tr><th>#</th><th>Error</th><th style=\"text-align:right;\">Ocurrencias</th></tr>");
        int i = 1;
        for (WeeklyReportData.TopError e : errors) {
            String key = e.getKey() != null
                    ? (e.getKey().length() > 100 ? e.getKey().substring(0, 100) + "..." : e.getKey())
                    : "-";
            sb.append("<tr>");
            sb.append("<td style=\"color:#6b7280;\">").append(i++).append("</td>");
            sb.append("<td>").append(esc(key)).append("</td>");
            sb.append("<td style=\"text-align:right;font-weight:bold;color:#dc2626;\">")
                    .append(e.getCount()).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }

    private String buildSuggestions(String suggestions) {
        if (suggestions == null || suggestions.isBlank()) {
            return "<p style=\"color:#9ca3af;font-size:11px\">No disponible.</p>";
        }
        StringBuilder sb = new StringBuilder();
        for (String line : suggestions.split("\n")) {
            String l = line.trim();
            if (l.isBlank()) continue;
            sb.append("<div style=\"padding:6px 0 6px 12px;border-left:3px solid #7c3aed;"
                            + "margin-bottom:6px;font-size:11px;color:#374151\">")
                    .append(esc(l)).append("</div>");
        }
        return sb.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String formatChange(double pct) {
        if (Math.abs(pct) < 0.1) return "Sin cambio";
        String sign = pct > 0 ? "+" : "";
        return sign + String.format(Locale.ROOT, "%.1f%%", pct);
    }

    private String changeClass(double pct) {
        if (Math.abs(pct) < 0.1) return "kpi-change-flat";
        return pct > 0 ? "kpi-change-up" : "kpi-change-down";
    }

    private String trendLabel(String trend) {
        if ("UP".equals(trend))     return "Empeoro";
        if ("DOWN".equals(trend))   return "Mejoro";
        return "Estable";
    }

    private String trendClass(String trend) {
        if ("UP".equals(trend))   return "trend-up";
        if ("DOWN".equals(trend)) return "trend-down";
        return "trend-flat";
    }

    private String statusColor(String status) {
        if ("CRIT".equals(status))    return "#dc2626";
        if ("WARN".equals(status))    return "#d97706";
        return "#16a34a";
    }

    private String statusBg(String status) {
        if ("CRIT".equals(status))    return "#fef2f2";
        if ("WARN".equals(status))    return "#fffbeb";
        return "#f0fdf4";
    }

    private String statusLabel(String status) {
        if ("CRIT".equals(status))    return "CRITICO";
        if ("WARN".equals(status))    return "ADVERTENCIA";
        return "SALUDABLE";
    }

    private byte[] renderPdf(String html) throws DocumentException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ITextRenderer renderer = new ITextRenderer();
        renderer.setDocumentFromString(html);
        renderer.layout();
        renderer.createPDF(baos);
        return baos.toByteArray();
    }
}