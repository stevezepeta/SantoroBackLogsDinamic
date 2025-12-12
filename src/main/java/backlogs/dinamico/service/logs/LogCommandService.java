package backlogs.dinamico.service.logs;

import backlogs.dinamico.controller.logs.LogCreateRequest;
import backlogs.dinamico.model.biometric.Person;
import backlogs.dinamico.model.log.LogEntry;
import backlogs.dinamico.repository.biometric.PersonRepository;
import backlogs.dinamico.repository.catalog.OfficeRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LogCommandService {

    private final MongoTemplate mongo;
    private final OfficeRepository officeRepo;
    private final PersonRepository personRepo;
    private final LogQueryService logQueryService;

    @Transactional
    public Map<String, Object> create(ObjectId tenantId, LogCreateRequest req) {

        Instant ts = (req.timestamp() != null) ? req.timestamp() : Instant.now();

        // Validacion de la oficina
        if (req.officeId() != null) {
            Long seq;
            try {
                seq = Long.valueOf(req.officeId());
            } catch (NumberFormatException ex) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "oficina_id_invalido"
                );
            }

            officeRepo.findByTenantIdAndSeq(tenantId, seq)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "oficina_no_encontrada"
                    ));
        }

        // Validacion de la persona
        if (req.personId() != null && !req.personId().isBlank()) {
            Person p = personRepo.findByTenantIdAndCurp(tenantId, req.personId())
                    .orElse(null);
        }

        // Construir y guardar entry
        LogEntry log = LogEntry.builder()
                .tenantId(tenantId)
                .timestamp(ts)
                .level(req.level())
                .message(req.message())
                .processType(req.processType())
                .device(req.device())
                .scanDevice(req.scanDevice())
                .scanType(req.scanType())
                .officeId(req.officeId())       // seq como String ("2", "3", ...)
                .personId(req.personId())
                .baseCode(req.baseCode())
                .errorCode(req.errorCode())
                .sessionToken(req.sessionToken())
                .system(req.system())
                .environment(req.environment())
                .extra(req.extra())
                .ingestAt(Instant.now())
                .build();

        mongo.save(log);

        return logQueryService.getOne(tenantId, log.getId());
    }

}
