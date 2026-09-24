package backlogs.dinamico.controller.analytics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/analytics/cases")
public class TicketProxyController {

    @Value("${app.ticket-api.tickets.url:https://ticket-api.grupo-santoro.com.mx/api/clientes/tickets}")
    private String ticketApiUrl;

    @Value("${app.ticket-api.login.url:https://ticket-api.grupo-santoro.com.mx/api/auth/login}")
    private String ticketApiLoginUrl;

    @Value("${app.ticket-api.login.email:admin@gob.com}")
    private String ticketApiLoginEmail;

    @Value("${app.ticket-api.login.password:Admin12345!}")
    private String ticketApiLoginPassword;

    /**
     * Obtiene un token JWT válido de la API de tickets autenticándose
     * con las credenciales de servicio configuradas.
     */
    private String getValidTicketApiToken() {
        try {
            RestTemplate restTemplate = new RestTemplate();

            Map<String, String> credentials = Map.of(
                    "email", ticketApiLoginEmail,
                    "password", ticketApiLoginPassword
            );

            log.info("[TicketProxy] Autenticando en ticket-api: {}", ticketApiLoginUrl);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    ticketApiLoginUrl,
                    credentials,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String token = (String) response.getBody().get("token");
                if (token == null || token.isBlank()) {
                    token = (String) response.getBody().get("access_token");
                }
                log.info("[TicketProxy] Token obtenido correctamente de ticket-api");
                return token;
            }
        } catch (Exception e) {
            log.error("[TicketProxy] Falló la autenticación en ticket-api: {}", e.getMessage());
        }
        return null;
    }

    @PostMapping(value = "/escalate-ticket", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> escalateCaseToTicket(
            @RequestParam("empresaId") String empresaId,
            @RequestParam("categoriaId") String categoriaId,
            @RequestParam("proyectoId") String proyectoId,
            @RequestParam("descripcion") String descripcion,
            @RequestPart(value = "imagenes", required = false) MultipartFile imagenes) {

        log.info("[TicketProxy] Iniciando escalamiento. Empresa: {}, Proyecto: {}", empresaId, proyectoId);

        // 1. Obtener token válido de la API de tickets
        String ticketApiToken = getValidTicketApiToken();
        if (ticketApiToken == null || ticketApiToken.isBlank()) {
            log.error("[TicketProxy] No se pudo obtener token de autenticación del servidor de tickets.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "No se pudo obtener autorización del servidor de tickets."));
        }

        try {
            // 2. Configurar headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("Authorization", "Bearer " + ticketApiToken);

            // 3. Armar body multipart
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("empresaId", empresaId);
            body.add("categoriaId", categoriaId);
            body.add("proyectoId", proyectoId);
            body.add("descripcion", descripcion);

            if (imagenes != null && !imagenes.isEmpty()) {
                body.add("imagenes", imagenes.getResource());
            }

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            RestTemplate restTemplate = new RestTemplate();

            // 4. Enviar petición a ticket-api
            ResponseEntity<String> response = restTemplate.postForEntity(
                    ticketApiUrl,
                    requestEntity,
                    String.class
            );

            log.info("[TicketProxy SUCCESS] Status: {} | Body: {}", response.getStatusCode(), response.getBody());
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());

        } catch (HttpStatusCodeException e) {
            log.error("[TicketProxy ERROR] Status: {} | Body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("[TicketProxy EXCEPTION]", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

}
