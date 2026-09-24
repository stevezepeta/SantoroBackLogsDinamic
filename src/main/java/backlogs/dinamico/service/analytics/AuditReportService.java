package backlogs.dinamico.service.analytics;

import backlogs.dinamico.api.dto.analytics.AuditCertificateRequest;
import backlogs.dinamico.tenant.TenantContext;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Servicio de generación de certificados formales de auditoría en PDF.
 */
@Service
@RequiredArgsConstructor
public class AuditReportService {

    private static final String COLLECTION = "log_events";
    private static final String DEFAULT_SYSTEM = "TRUSTVALUE";

    // FORMATOS NATURALES DE FECHA
    private static final DateTimeFormatter NATURAL_DATETIME_FMT = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private static final DateTimeFormatter NATURAL_DATE_ONLY_FMT = DateTimeFormatter
            .ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.systemDefault());

    private final MongoTemplate mongoTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.audit.certificate.secret:backlogs-audit-server-secret}")
    private String serverSecret;

    public byte[] generateCertificate(AuditCertificateRequest req) {
        ObjectId tenantId = TenantContext.requireTenantId();
        String effectiveSystem = StringUtils.hasText(req.system()) ? req.system() : DEFAULT_SYSTEM;
        String username = req.username().trim();

        Instant from = parseToInstant(req.fromDate(), false);
        Instant to = parseToInstant(req.toDate(), true);

        if (from.isAfter(to)) {
            Instant tmp = from;
            from = to;
            to = tmp;
        }

        List<Document> events = fetchEvents(tenantId, effectiveSystem, username, from, to);

        String folio = generateFolio();
        String emissionFormatted = NATURAL_DATETIME_FMT.format(Instant.now());
        String digitalSeal = computeDigitalSeal(folio, username, events.size());

        com.lowagie.text.Document pdfDocument = new com.lowagie.text.Document(PageSize.A4, 40, 40, 40, 40);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(pdfDocument, baos);
            pdfDocument.open();

            addHeader(pdfDocument, folio, emissionFormatted);
            addMetadataTable(pdfDocument, events, username, effectiveSystem, from, to, req.reason());
            addDictamen(pdfDocument, effectiveSystem, events.size());
            if (!events.isEmpty()) {
                addEvidenceTable(pdfDocument, events);
            }
            addFooterSeal(pdfDocument, digitalSeal);

            pdfDocument.close();
        } catch (DocumentException e) {
            throw new RuntimeException("No se pudo generar el certificado de auditoría", e);
        }
        return baos.toByteArray();
    }

    private Instant parseToInstant(String dateStr, boolean isEnd) {
        if (dateStr == null || dateStr.isBlank()) {
            return Instant.now();
        }
        try {
            if (dateStr.contains("T")) {
                return Instant.parse(dateStr);
            }
            LocalDate localDate = LocalDate.parse(dateStr);
            return isEnd
                    ? localDate.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC)
                    : localDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (Exception e) {
            return Instant.now();
        }
    }

    private List<Document> fetchEvents(ObjectId tenantId, String system, String username, Instant from, Instant to) {
        Criteria criteria = Criteria.where("tenant_id").is(tenantId)
                .and("system").regex("^" + Pattern.quote(system) + "$", "i")
                .and("eventTime").gte(Date.from(from)).lte(Date.from(to))
                .and("actor.username").is(username);

        Query query = new Query(criteria).with(Sort.by(Sort.Direction.ASC, "eventTime"));
        return mongoTemplate.find(query, Document.class, COLLECTION);
    }

    private String generateFolio() {
        int year = java.time.Year.now(ZoneId.systemDefault()).getValue();
        byte[] bytes = new byte[3];
        secureRandom.nextBytes(bytes);
        return "AUD-" + year + "-" + bytesToHex(bytes).toUpperCase();
    }

    private String computeDigitalSeal(String folio, String username, int totalEvents) {
        try {
            String payload = folio + "|" + username + "|" + totalEvents + "|" + serverSecret;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash).toUpperCase();
        } catch (Exception e) {
            throw new RuntimeException("Error al calcular el sello digital", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // ── Secciones del PDF ────────────────────────────────────────────────────

    private void addHeader(com.lowagie.text.Document pdfDocument, String folio, String emissionFormatted)
            throws DocumentException {

        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{1.4f, 3.6f});

        // Intentar cargar el logo corporativo de la carpeta resources
        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setBackgroundColor(null);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        try {
            InputStream is = getClass().getResourceAsStream("/static/LogosGS.png");
            if (is == null) is = getClass().getResourceAsStream("/LogosGS.png");

            if (is != null) {
                byte[] bytes = is.readAllBytes();
                Image logoImg = Image.getInstance(bytes);
                logoImg.scaleToFit(165, 65);
                logoCell.addElement(logoImg);
            } else {
                // Insignia estilizada si no hay imagen de logo
                Paragraph fallbackText = new Paragraph("SANTORO\nLOGS", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(41, 128, 185)));
                logoCell.addElement(fallbackText);
            }
        } catch (Exception e) {
            logoCell.addElement(new Paragraph("GRUPO SANTORO", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
        }
        header.addCell(logoCell);

        // Título del Certificado
        PdfPCell titleCell = new PdfPCell();
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        Paragraph title = new Paragraph(
                "CERTIFICADO DE AUDITORÍA Y TRAZABILIDAD DIGITAL",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, new Color(33, 37, 41)));
        title.setAlignment(Element.ALIGN_RIGHT);
        titleCell.addElement(title);
        header.addCell(titleCell);

        pdfDocument.add(header);
        pdfDocument.add(Chunk.NEWLINE);

        // Folio y Fecha en formato limpio
        Paragraph folioLine = new Paragraph(
                "Folio Único de Verificación: " + folio,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(52, 58, 64)));
        folioLine.setAlignment(Element.ALIGN_RIGHT);
        pdfDocument.add(folioLine);

        Paragraph emissionLine = new Paragraph(
                "Fecha/Hora de Emisión: " + emissionFormatted + " hrs",
                FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY));
        emissionLine.setAlignment(Element.ALIGN_RIGHT);
        pdfDocument.add(emissionLine);

        pdfDocument.add(Chunk.NEWLINE);
    }

    private void addMetadataTable(com.lowagie.text.Document pdfDocument, List<Document> events, String username,
                                  String system, Instant from, Instant to, String reason)
            throws DocumentException {

        String fullName = events.isEmpty() ? "No disponible" : firstText(events.get(0), "actor.fullName", username);
        String periodFormatted = NATURAL_DATE_ONLY_FMT.format(from) + " al " + NATURAL_DATE_ONLY_FMT.format(to);

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.2f, 3.8f});

        metadataRow(table, "Nombre Completo:", fullName);
        metadataRow(table, "Username:", username);
        metadataRow(table, "Sistema/App:", system);
        metadataRow(table, "Periodo Auditado:", periodFormatted);
        metadataRow(table, "Motivo de Consulta:", StringUtils.hasText(reason) ? reason : "Auditoría Administrativa");

        pdfDocument.add(table);
        pdfDocument.add(Chunk.NEWLINE);
    }

    private void metadataRow(PdfPTable table, String label, String value) {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(73, 80, 87));
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(33, 37, 41));

        PdfPCell labelCell = new PdfPCell(new Paragraph(label, labelFont));
        labelCell.setBorder(Rectangle.BOTTOM);
        labelCell.setBorderColor(new Color(222, 226, 230));
        labelCell.setPadding(6);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Paragraph(value, valueFont));
        valueCell.setBorder(Rectangle.BOTTOM);
        valueCell.setBorderColor(new Color(222, 226, 230));
        valueCell.setPadding(6);
        table.addCell(valueCell);
    }

    private void addDictamen(com.lowagie.text.Document pdfDocument, String system, int totalEvents) throws DocumentException {
        Font dictamenFont = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(33, 37, 41));
        Paragraph dictamen;
        if (totalEvents == 0) {
            dictamen = new Paragraph(
                    "SE CERTIFICA QUE: En las bases de datos de la plataforma " + system
                            + ", NO SE ENCONTRÓ NINGUNA ACTIVIDAD registrada para el usuario en el periodo especificado.",
                    dictamenFont);
        } else {
            dictamen = new Paragraph(
                    "SE CERTIFICA QUE: Se encontraron " + totalEvents
                            + " registros de actividad formalmente validados en la plataforma " + system + ".",
                    dictamenFont);
        }
        dictamen.setAlignment(Element.ALIGN_JUSTIFIED);
        dictamen.setSpacingAfter(12);
        pdfDocument.add(dictamen);
    }

    private void addEvidenceTable(com.lowagie.text.Document pdfDocument, List<Document> events) throws DocumentException {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);

        // ANCHOS REBALANCEADOS: Se redujo Dirección IP (1.1f) para darle espacio a Tipo de Evento (3.1f)
        table.setWidths(new float[]{1.8f, 3.1f, 2.3f, 1.1f, 1.7f});
        table.setHeaderRows(1);

        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        Color headerColor = new Color(52, 58, 64);

        String[] headers = {"Timestamp", "Tipo de Evento", "Dispositivo / SO", "Dirección IP", "Estatus / Resultado"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Paragraph(h, headerFont));
            cell.setBackgroundColor(headerColor);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(5);
            table.addCell(cell);
        }

        // Fuente a 7.5pt para ajuste perfecto de textos largos
        Font rowFont = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, new Color(33, 37, 41));
        for (Document event : events) {
            Instant ts = toInstant(event.get("eventTime"));
            String timeStr = ts != null ? NATURAL_DATETIME_FMT.format(ts) : "N/A";

            table.addCell(cell(timeStr, rowFont));
            table.addCell(cell(event.getString("eventType"), rowFont));
            table.addCell(cell(extractDevice(event), rowFont));
            table.addCell(cell(extractIp(event), rowFont));
            table.addCell(cell(firstOutcome(event), rowFont));
        }

        pdfDocument.add(table);
        pdfDocument.add(Chunk.NEWLINE);
    }

    private PdfPCell cell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Paragraph(text != null ? text : "N/A", font));
        cell.setPadding(4);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    private void addFooterSeal(com.lowagie.text.Document pdfDocument, String seal) throws DocumentException {
        Font sealFont = FontFactory.getFont(FontFactory.COURIER, 7, Color.DARK_GRAY);
        Paragraph p = new Paragraph("SELLO DIGITAL DE AUTENTICIDAD (SHA-256):\n" + seal, sealFont);
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingBefore(10);
        pdfDocument.add(p);

        Font noteFont = FontFactory.getFont(FontFactory.HELVETICA, 7, Color.GRAY);
        Paragraph note = new Paragraph(
                "Este sello permite verificar la integridad del certificado. Cualquier alteración invalida el documento.",
                noteFont);
        note.setAlignment(Element.ALIGN_CENTER);
        pdfDocument.add(note);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String firstText(Document doc, String dottedPath, String fallback) {
        if (doc == null) return fallback;
        String[] parts = dottedPath.split("\\.");
        Object current = doc;
        for (String part : parts) {
            if (current instanceof Document d) {
                current = d.get(part);
            } else {
                return fallback;
            }
        }
        String s = current instanceof String str ? str : null;
        return StringUtils.hasText(s) ? s : fallback;
    }

    private static String extractDevice(Document event) {
        // 1. Lectura directa desde la estructura 'meta' de TrustValue
        Object metaObj = event.get("meta");
        if (metaObj instanceof Document meta) {
            String deviceName = firstNonBlank(
                    asString(meta.get("deviceName")),
                    asString(meta.get("deviceModel")),
                    asString(meta.get("deviceBrand"))
            );
            String platform = asString(meta.get("platform"));

            if (deviceName != null && !deviceName.equals("N/A")) {
                return platform != null ? deviceName + " (" + platform + ")" : deviceName;
            }
        }

        // 2. Fallback para otros sistemas con estructuras distintas
        Object device = event.get("device");
        String d = asString(device);
        if (d != null) return d;
        if (device instanceof Document dd) {
            d = firstNonBlank(asString(dd.get("model")), asString(dd.get("name")), asString(dd.get("code")));
            if (d != null) return d;
        }

        return firstNonBlank(
                asString(nested(event, "systemAndDevices", "device")),
                asString(nested(event, "deviceInfo", "model")),
                asString(nested(event, "requestInfo", "userAgent")),
                asString(nested(event, "payload", "device"))
        );
    }

    private static String extractIp(Document event) {
        return firstNonBlank(
                asString(event.get("clientIp")),
                asString(event.get("ip")),
                asString(nested(event, "requestInfo", "clientIp")),
                asString(nested(event, "meta", "ip")),
                asString(nested(event, "meta", "clientIp")),
                asString(nested(event, "http", "clientIp")),
                asString(nested(event, "remoteConnection", "sourceIp"))
        );
    }

    private static String firstOutcome(Document event) {
        return firstNonBlank(
                asString(event.get("outcome")),
                asString(event.get("status"))
        );
    }

    private static Object nested(Document root, String parent, String child) {
        if (root == null) return null;
        Object p = root.get(parent);
        return p instanceof Document d ? d.get(child) : null;
    }

    private static String asString(Object value) {
        return value instanceof String s && !s.isBlank() ? s.trim() : null;
    }

    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (StringUtils.hasText(c)) return c;
        }
        return "N/A";
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Date d) return d.toInstant();
        if (value instanceof Instant i) return i;
        return null;
    }
}