package backlogs.dinamico.service.ai;

import backlogs.dinamico.model.ai.WeeklyReportData;
import backlogs.dinamico.model.core.Organization;
import backlogs.dinamico.model.core.User;
import backlogs.dinamico.model.core.UserRole;
import backlogs.dinamico.repository.core.OrganizationRepository;
import backlogs.dinamico.repository.core.RoleRepository;
import backlogs.dinamico.repository.core.UserRepository;
import backlogs.dinamico.repository.core.UserRoleRepository;
import backlogs.dinamico.service.email.WeeklyReportPdfService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyReportSchedulerService {

    private final OrganizationRepository   orgRepo;
    private final UserRoleRepository       userRoleRepo;
    private final UserRepository           userRepo;
    private final RoleRepository           roleRepo;
    private final WeeklyReportBuilderService builder;
    private final WeeklyReportPdfService   pdfService;
    private final JavaMailSender           mailSender;

    // ── Cada domingo a las 19:00 hora México ──────────────────────────────────
    @Scheduled(cron = "0 0 19 * * SUN", zone = "America/Mexico_City")
    public void runWeeklyReport() {
        log.info("[WeeklyReport] Iniciando ciclo semanal...");

        List<Organization> orgs = orgRepo.findByStatusNot("disabled");
        if (orgs == null || orgs.isEmpty()) {
            log.info("[WeeklyReport] Sin organizaciones activas.");
            return;
        }

        for (Organization org : orgs) {
            try {
                processOrg(org);
            } catch (Exception e) {
                log.error("[WeeklyReport] Error procesando org {}: {}",
                        org.getId(), e.getMessage());
            }
        }

        log.info("[WeeklyReport] Ciclo completado.");
    }

    private void processOrg(Organization org) {
        ObjectId tenantId = org.getId();
        log.info("[WeeklyReport] Procesando org: {} ({})", org.getName(), tenantId);

        // Construir datos comparativos
        WeeklyReportData data = builder.build(tenantId);

        if (data.getTotalThisWeek() == 0) {
            log.info("[WeeklyReport] Sin actividad esta semana para org {}", org.getName());
            return;
        }

        // Generar PDF
        byte[] pdf = pdfService.generate(data);
        if (pdf == null) {
            log.error("[WeeklyReport] PDF nulo para org {} — omitiendo envío", org.getName());
            return;
        }

        // Obtener destinatarios ORG_OWNER
        List<String> recipients = getOrgOwnerEmails(tenantId);
        if (recipients.isEmpty()) {
            log.warn("[WeeklyReport] Sin destinatarios ORG_OWNER para org {}", org.getName());
            return;
        }

        // Enviar correo
        sendWeeklyEmail(recipients, data, pdf, org.getName());
        log.info("[WeeklyReport] Reporte semanal enviado a {} destinatarios para org {}",
                recipients.size(), org.getName());
    }

    private List<String> getOrgOwnerEmails(ObjectId tenantId) {
        List<String> emails = new ArrayList<>();
        try {
            // Buscar roles con código ORG_OWNER
            var ownerRoles = roleRepo.findAll().stream()
                    .filter(r -> r.getTenantId() != null
                            && r.getTenantId().equals(tenantId)
                            && r.getCode() != null
                            && "ORG_OWNER".equals(r.getCode().name()))
                    .toList();

            if (ownerRoles.isEmpty()) return emails;

            Set<ObjectId> ownerRoleIds = new HashSet<>();
            ownerRoles.forEach(r -> ownerRoleIds.add(r.getId()));

            // Buscar UserRoles con esos roleIds
            List<UserRole> links = userRoleRepo.findByTenantId(tenantId).stream()
                    .filter(ur -> ownerRoleIds.contains(ur.getRoleId()))
                    .toList();

            // Obtener emails de los usuarios
            for (UserRole ur : links) {
                userRepo.findById(ur.getUserId()).ifPresent(user -> {
                    if (user.getEmail() != null && !user.getEmail().isBlank()) {
                        emails.add(user.getEmail());
                    }
                });
            }
        } catch (Exception e) {
            log.error("[WeeklyReport] Error obteniendo ORG_OWNER emails: {}", e.getMessage());
        }
        return emails;
    }

    private void sendWeeklyEmail(List<String> recipients, WeeklyReportData data,
                                 byte[] pdf, String orgName) {
        try {
            jakarta.mail.internet.MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");

            helper.setFrom("noreply@grupo-santoro.com.mx", "DataLogs - " + orgName);
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject("Resumen Ejecutivo Semanal — " + data.getWeekLabel()
                    + " — " + statusEmoji(data.getStatus()) + " " + statusLabel(data.getStatus()));

            String body = buildEmailBody(data, orgName);
            helper.setText(body, true);

            helper.addAttachment(
                    "resumen-semanal-" + data.getWeekLabel().replace("/", "-")
                            .replace(" ", "").replace("—", "-") + ".pdf",
                    new org.springframework.core.io.ByteArrayResource(pdf),
                    "application/pdf"
            );

            mailSender.send(msg);
        } catch (Exception e) {
            log.error("[WeeklyReport] Error enviando correo: {}", e.getMessage());
        }
    }

    private String buildEmailBody(WeeklyReportData data, String orgName) {
        String statusColor = data.getStatus().equals("CRIT") ? "#dc2626"
                : data.getStatus().equals("WARN") ? "#d97706" : "#16a34a";
        String totalChange = data.getTotalChangePct() > 0
                ? "+" + String.format(Locale.ROOT, "%.1f%%", data.getTotalChangePct())
                : String.format(Locale.ROOT, "%.1f%%", data.getTotalChangePct());

        return "<html><body style='font-family:Arial,sans-serif;background:#f9fafb;padding:20px;'>"
                + "<div style='max-width:600px;margin:0 auto;background:#fff;"
                +      "border-radius:12px;overflow:hidden;box-shadow:0 4px 12px rgba(0,0,0,0.1);'>"
                + "<div style='background:#111827;padding:24px 32px;border-bottom:4px solid #7c3aed;'>"
                + "  <div style='font-size:22px;font-weight:bold;color:#fff;'>DataLogs</div>"
                + "  <div style='font-size:11px;color:#6b7280;margin-top:4px;letter-spacing:2px;'>"
                +      "RESUMEN EJECUTIVO SEMANAL</div>"
                + "</div>"
                + "<div style='padding:24px 32px;'>"
                + "  <p style='color:#374151;'>Estimado equipo de <strong>" + esc(orgName) + "</strong>,</p>"
                + "  <p style='color:#374151;margin-top:12px;'>"
                +      "Adjuntamos el resumen ejecutivo de la semana <strong>"
                + esc(data.getWeekLabel()) + "</strong>.</p>"
                + "  <div style='background:#f3f4f6;border-radius:8px;padding:16px;margin:16px 0;'>"
                + "    <table width='100%' cellpadding='8' cellspacing='0'>"
                + "      <tr>"
                + "        <td><div style='font-size:10px;color:#9ca3af;'>TOTAL EVENTOS</div>"
                + "            <div style='font-size:20px;font-weight:bold;'>"
                +                  data.getTotalThisWeek() + "</div>"
                + "            <div style='font-size:11px;color:#6b7280;'>"
                +                  totalChange + " vs semana anterior</div></td>"
                + "        <td><div style='font-size:10px;color:#9ca3af;'>ERROR RATE</div>"
                + "            <div style='font-size:20px;font-weight:bold;color:" + statusColor + ";'>"
                +                  String.format(Locale.ROOT, "%.1f%%", data.getErrorRateThisWeek() * 100)
                +              "</div></td>"
                + "        <td><div style='font-size:10px;color:#9ca3af;'>ESTADO</div>"
                + "            <div style='font-size:16px;font-weight:bold;color:" + statusColor + ";'>"
                +                  statusEmoji(data.getStatus()) + " " + statusLabel(data.getStatus())
                +              "</div></td>"
                + "      </tr>"
                + "    </table>"
                + "  </div>"
                + "  <p style='color:#6b7280;font-size:12px;'>El reporte detallado con analisis"
                +     " comparativo se encuentra adjunto en PDF.</p>"
                + "</div>"
                + "<div style='background:#f9fafb;padding:16px 32px;"
                +      "border-top:1px solid #e5e7eb;font-size:11px;color:#9ca3af;'>"
                + "  DataLogs - Grupo Santoro - Reporte generado automaticamente."
                + "</div>"
                + "</div></body></html>";
    }

    private String statusEmoji(String status) {
        if ("CRIT".equals(status))    return "CRITICO";
        if ("WARN".equals(status))    return "ADVERTENCIA";
        return "SALUDABLE";
    }

    private String statusLabel(String status) {
        if ("CRIT".equals(status))    return "Estado Critico";
        if ("WARN".equals(status))    return "Con Advertencias";
        return "Sin Incidencias";
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}