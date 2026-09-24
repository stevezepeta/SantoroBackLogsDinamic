package backlogs.dinamico.service.analytics;

import backlogs.dinamico.api.dto.analytics.OperationalEventCard;
import backlogs.dinamico.api.dto.analytics.OperationalStatus;
import backlogs.dinamico.config.OperationalMessageCatalog;
import backlogs.dinamico.model.log.LogEvent;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OperationalTranslationServiceTest {

    @Test
    void translatesSuccessfulEvidenceUpload() {
        OperationalMessageCatalog catalog = buildCatalog();
        OperationalTranslationService service = new OperationalTranslationService(catalog);

        LogEvent event = LogEvent.builder()
                .id(new ObjectId())
                .system("TRUSTVALUE")
                .caseId("CAS-001")
                .eventTime(Instant.parse("2026-07-13T10:00:00Z"))
                .eventType("APP_EVENT")
                .eventCode("ENVIAR_EVIDENCIAS")
                .status("SUCCESS")
                .outcome("COMPLETED")
                .message("Evidencias recibidas")
                .actor(new LogEvent.Actor("USR-1", "USER", "jlopez", "Juan López"))
                .location(new LogEvent.Location("OFF-TOL", "Toluca Centro", "Toluca", "MX"))
                .geo(new LogEvent.GeoPoint("Point", List.of(-99.6557, 19.2826), 8))
                .build();

        OperationalEventCard card = service.translate(event);

        assertNotNull(card);
        assertEquals("Evidencias cargadas", card.getTitle());
        assertEquals("El usuario completó la carga de fotografías con éxito.", card.getDescription());
        assertEquals(OperationalStatus.SUCCESS, card.getStatus());
        assertEquals("Juan López", card.getActorName());
        assertEquals("Toluca Centro", card.getLocationName());
        assertEquals(19.2826, card.getLatitude(), 0.0001);
        assertEquals(-99.6557, card.getLongitude(), 0.0001);
        assertFalse(card.isHasErrorExplanation());
        assertNull(card.getErrorExplanation());
    }

    @Test
    void translatesRejectedEvidenceUploadWithLazyExplanationPlaceholder() {
        OperationalMessageCatalog catalog = buildCatalog();
        OperationalTranslationService service = new OperationalTranslationService(catalog);

        LogEvent event = LogEvent.builder()
                .id(new ObjectId())
                .system("TRUSTVALUE")
                .caseId("CAS-002")
                .eventTime(Instant.now())
                .eventType("APP_EVENT")
                .eventCode("ENVIAR_EVIDENCIAS")
                .status("REJECTED")
                .outcome("ERROR")
                .message("Timeout")
                .actor(new LogEvent.Actor("USR-2", "USER", "mgarcia", "María García"))
                .build();

        OperationalEventCard card = service.translate(event);

        assertNotNull(card);
        assertEquals("Fallo al enviar evidencias", card.getTitle());
        assertEquals(OperationalStatus.ERROR, card.getStatus());
        assertTrue(card.isHasErrorExplanation());
        assertNull(card.getErrorExplanation());
    }

    @Test
    void fallsBackWhenMessageNotFound() {
        OperationalMessageCatalog catalog = new OperationalMessageCatalog();
        OperationalTranslationService service = new OperationalTranslationService(catalog);

        LogEvent event = LogEvent.builder()
                .id(new ObjectId())
                .system("UNKNOWN")
                .eventCode("UNKNOWN_EVENT")
                .status("SUCCESS")
                .message("Mensaje técnico original")
                .build();

        OperationalEventCard card = service.translate(event);

        assertNotNull(card);
        assertEquals("UNKNOWN_EVENT", card.getTitle());
        assertEquals("Mensaje técnico original", card.getDescription());
        assertEquals(OperationalStatus.SUCCESS, card.getStatus());
    }

    private OperationalMessageCatalog buildCatalog() {
        OperationalMessageCatalog catalog = new OperationalMessageCatalog();

        OperationalMessageCatalog.OperationalMessage success = new OperationalMessageCatalog.OperationalMessage();
        success.setTitle("Evidencias cargadas");
        success.setDescription("El usuario completó la carga de fotografías con éxito.");
        success.setAction("Continuar con el cierre de la jornada.");
        success.setIcon("image-check");

        OperationalMessageCatalog.OperationalMessage rejected = new OperationalMessageCatalog.OperationalMessage();
        rejected.setTitle("Fallo al enviar evidencias");
        rejected.setDescription("No se pudieron recibir las fotografías del usuario.");
        rejected.setAction("Verificar conectividad.");
        rejected.setIcon("image-broken");

        catalog.setCatalog(Map.of(
                "TRUSTVALUE", Map.of(
                        "ENVIAR_EVIDENCIAS", Map.of(
                                "SUCCESS", success,
                                "REJECTED", rejected
                        )
                )
        ));

        return catalog;
    }
}
