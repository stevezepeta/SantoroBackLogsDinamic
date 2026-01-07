package backlogs.dinamico.service.dashboard;

import backlogs.dinamico.api.dto.common.PageResult;
import backlogs.dinamico.api.dto.passport.PassportEventCreateReq;
import backlogs.dinamico.model.catalog.Office;
import backlogs.dinamico.model.core.User;
import backlogs.dinamico.model.passport.PassportEvent;
import backlogs.dinamico.repository.catalog.OfficeRepository;
import backlogs.dinamico.repository.core.UserRepository;
import backlogs.dinamico.repository.passport.PassportEventRepository;
import backlogs.dinamico.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PassportEventService {

    private final PassportEventRepository eventRepo;
    private final UserRepository userRepo;
    private final OfficeRepository officeRepo;

    private final MongoTemplate mongoTemplate;

    public PassportEvent createEvent(ObjectId tenantId, PassportEventCreateReq req) {

        // Resolver usuario
        ObjectId userId;
        try {
            userId = new ObjectId(req.userId());
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "userId no es un ObjectId válido: " + req.userId()
            );
        }

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Usuario no encontrado para userId=" + req.userId()
                ));

        // Resolver oficina
        ObjectId officeId;
        try {
            officeId = new ObjectId(req.officeId());
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "officeId no es un ObjectId válido: " + req.officeId()
            );
        }

        Office office = officeRepo.findById(officeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Oficina no encontrada para officeId=" + req.officeId()
                ));

        // Mapear las coordenadas
        Office.GeoPoint geo = office.getGeo();
        PassportEvent.GeoPointInfo geoInfo = null;

        if (geo != null && geo.getCoordinates() != null && !geo.getCoordinates().isEmpty()) {
            geoInfo = new PassportEvent.GeoPointInfo(
                    geo.getType(),
                    geo.getCoordinates()
            );
        }

        // Normalizar fecha del evento
        Instant eventTime = (req.eventTime() != null) ? req.eventTime() : Instant.now();

        // Construir entidad para Mongo
        PassportEvent event = PassportEvent.builder()
                .tenantId(tenantId)
                .system(req.system())
                .operationType(req.operationType())
                .status(normalizeStatus(req.status()))
                .eventTime(eventTime)
                .message(req.message())

                .passport(new PassportEvent.PassportInfo(
                        req.passportNumber(),
                        req.personId(),
                        req.fullName(),
                        req.nationality()
                ))

                .office(new PassportEvent.OfficeInfo(
                        office.getId().toHexString(),
                        office.getName(),
                        office.getAddress(),
                        office.getCountryId(),
                        geoInfo
                ))

                .channel(req.channel())

                .user(new PassportEvent.UserInfo(
                        user.getId().toHexString(),
                        user.getEmail(),
                        user.getName()
                ))

                .sla(req.sla() == null ? null :
                        new PassportEvent.SlaInfo(
                                req.sla().startTime(),
                                req.sla().endTime(),
                                req.sla().elapsedSeconds()
                        ))

                .reason(req.reason() == null ? null :
                        new PassportEvent.ReasonInfo(
                                req.reason().code(),
                                req.reason().description()
                        ))

                .meta(req.meta())
                .build();

        return eventRepo.save(event);
    }


    // Busqueda con filtros + paginacion
    public Page<PassportEvent> searchEvents(
            Instant from,
            Instant to,
            String status,
            String operationType,
            String officeId,
            String userId,
            String channel,
            String messageContains,
            String reasonCode,
            int page,
            int size,
            String sortBy,
            String sortDir
    ) {

        var tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalArgumentException("Token no valido");
        }

        List<Criteria> criteriaList = new ArrayList<>();

        // Siempre se filtra por tenant
        criteriaList.add(Criteria.where("tenantId").is(tenantId));
        criteriaList.add(Criteria.where("system").is("PASSPORT_PA"));
        criteriaList.add(Criteria.where("eventTime").gte(from).lte(to));

        if (status != null && !status.isBlank()) {
            criteriaList.add(Criteria.where("status").is(status));
        }

        if (operationType != null && !operationType.isBlank()) {
            criteriaList.add(Criteria.where("operationType").is(operationType));
        }

        if (officeId != null && !officeId.isBlank()) {
            criteriaList.add(Criteria.where("office.officeId").is(officeId));
        }

        if (userId != null && !userId.isBlank()) {
            criteriaList.add(Criteria.where("user.userId").is(userId));
        }

        if (channel != null && !channel.isBlank()) {
            criteriaList.add(Criteria.where("channel").is(channel));
        }

        if (messageContains != null && !messageContains.isBlank()) {
            // regex case-insensitive: .*texto.*
            Pattern pattern = Pattern.compile(
                    ".*" + Pattern.quote(messageContains) + ".*",
                    Pattern.CASE_INSENSITIVE
            );
            criteriaList.add(Criteria.where("message").regex(pattern));
        }

        if (StringUtils.hasText(reasonCode)) {
            criteriaList.add(
                    Criteria.where("reason.code").is(reasonCode.toUpperCase())
            );
        }

        Criteria criteria = new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));
        Query query = new Query(criteria);

        // Order
        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDir)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        String sortField = (sortBy != null && !sortBy.isBlank())
                ? sortBy
                : "eventTime";

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(direction, sortField));
        query.with(pageRequest);

        List<PassportEvent> items = mongoTemplate.find(query, PassportEvent.class, "passport_events");

        long total = mongoTemplate.count(
                Query.of(query).limit(-1).skip(-1),
                "passport_events"
        );

        return new PageImpl<>(items, pageRequest, total);
    }

    public Optional<PassportEvent> findById(ObjectId tenantId, ObjectId id) {
        return eventRepo.findByIdAndTenantId(id, tenantId);
    }


    private String normalizeStatus(String raw) {

        if (raw == null) {
            return "DESCONOCIDO";
        }

        // Quitar acentos, pasar mayusculas y eliminar espacios/guiones bajos
        String s = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replace("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replace(" ", "")
                .replace("_", "");

        // Mapear a los 4 estados
        return switch (s) {
            case "EMITIDO", "EMISION", "ISSUED" -> "EMITIDO";
            case "RECHAZADO", "RECHAZO", "DENEGADO", "DENIED" -> "RECHAZADO";
            case "CANCELADO", "CANCELACION", "CANCELED" -> "CANCELADO";
            case "ENTRAMITE", "ENPROCESO", "PENDIENTE" -> "ENTRAMITE";
            default -> s; // por si usamos otros estados en el futuro
        };

    }

}
