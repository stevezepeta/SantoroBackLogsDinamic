package backlogs.dinamico.service.biometric;

import backlogs.dinamico.config.BiometricProps;
import backlogs.dinamico.model.biometric.FingerPrint;
import backlogs.dinamico.model.biometric.Person;
import backlogs.dinamico.model.catalog.Office;
import backlogs.dinamico.repository.biometric.FingerPrintRepository;
import backlogs.dinamico.repository.biometric.PersonRepository;
import backlogs.dinamico.repository.catalog.OfficeRepository;
import backlogs.dinamico.tenant.TenantContext;
import com.machinezoo.sourceafis.FingerprintImage;
import com.machinezoo.sourceafis.FingerprintImageOptions;
import com.machinezoo.sourceafis.FingerprintMatcher;
import com.machinezoo.sourceafis.FingerprintTemplate;
import lombok.RequiredArgsConstructor;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.*;

import static java.lang.Math.round;

@Slf4j
@Service
@RequiredArgsConstructor
public class BiometricService {

    private final PersonRepository personRepo;
    private final FingerPrintRepository fpRepo;
    private final BiometricProps props;
    private final OfficeRepository officeRepo;

    private static final String[] KEYS = {
            "thumbLeft","indexLeft","middleLeft","ringLeft","littleLeft",
            "thumbRight","indexRight","middleRight","ringRight","littleRight"
    };

    @Transactional
    public void enroll(String curp, String name, Map<String, MultipartFile> files, MultipartFile facePhoto) throws IOException {

        ObjectId tenantId = requireTenant();

        Person person = personRepo.findByTenantIdAndCurp(tenantId, curp)
                .orElseGet(() -> personRepo.save(Person.builder()
                        .tenantId(tenantId)
                        .curp(curp)
                        .name(name)
                        .build()));

        FingerPrint fp = fpRepo.findByTenantIdAndPersonId(tenantId, person.getId())
                .orElseGet(() -> FingerPrint.builder()
                        .tenantId(tenantId)
                        .personId(person.getId())
                        .build());

        for (String k : KEYS) {
            MultipartFile mf = files.get(k);
            if (mf != null && !mf.isEmpty()) {
                String path = saveFile(tenantId, curp, k, mf, props.getDirFp());
                setFingerPath(fp, k, path);
            }
        }

        // foto facila
        if (facePhoto != null && !facePhoto.isEmpty()) {
            String path = saveFile(tenantId, curp, "face", facePhoto, props.getDirFp());
            person.setFacePhotoPath(path);
            personRepo.save(person);
        }

        fpRepo.save(fp);
    }

    public record VerifyResult(boolean matched, double score, String matchedFinger) {

    }

    public VerifyResult verify(String curp, Map<String, MultipartFile> files) throws IOException {
        ObjectId tenantId = requireTenant();
        Person person = personRepo.findByTenantIdAndCurp(tenantId, curp).orElse(null);
        if (person == null) return new VerifyResult(false, 0, null);

        FingerPrint fp = fpRepo.findByTenantIdAndPersonId(tenantId, person.getId()).orElse(null);
        if (fp == null) return new VerifyResult(false, 0, null);

        var opts = new FingerprintImageOptions().dpi(500);
        for (String k : KEYS) {
            MultipartFile uploaded = files.get(k);
            String storedPath = getFingerPath(fp, k);
            if (uploaded != null && !uploaded.isEmpty() && storedPath != null) {
                byte[] a = uploaded.getBytes();
                byte[] b = Files.readAllBytes(Paths.get(storedPath));
                var t1 = new FingerprintTemplate(new FingerprintImage(a, opts));
                var t2 = new FingerprintTemplate(new FingerprintImage(b, opts));
                double score = new FingerprintMatcher(t1).match(t2);

                if (score >= props.getThreshold()) {
                    return new VerifyResult(true, round(score), k);
                }
            }
        }
        return new VerifyResult(false, 0, null);
    }

    private ObjectId requireTenant() {
        var id = TenantContext.getTenantId();
        if (id == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Tenant requerido");
        return id;
    }

    private String saveFile(ObjectId tenantId, String curp, String label, MultipartFile mf, String baseDir) throws IOException {
        Path dir = Paths.get(baseDir, tenantId.toHexString(), curp);
        Files.createDirectories(dir);
        String filename = label + "_" + System.currentTimeMillis() + ".jpg";
        Path path = dir.resolve(filename);
        Files.write(path, mf.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return path.toString();
    }

    private void setFingerPath(FingerPrint fp, String key, String path) {
        switch (key) {
            case "thumbLeft" -> fp.setThumbLeft(path);
            case "indexLeft" -> fp.setIndexLeft(path);
            case "middleLeft" -> fp.setMiddleLeft(path);
            case "ringLeft" -> fp.setRingLeft(path);
            case "littleLeft" -> fp.setLittleLeft(path);
            case "thumbRight" -> fp.setThumbRight(path);
            case "indexRight" -> fp.setIndexRight(path);
            case "middleRight" -> fp.setMiddleRight(path);
            case "ringRight" -> fp.setRingRight(path);
            case "littleRight" -> fp.setLittleRight(path);
        }
    }

    private String getFingerPath(FingerPrint fp, String key) {
        return switch (key) {
            case "thumbLeft" -> fp.getThumbLeft();
            case "indexLeft" -> fp.getIndexLeft();
            case "middleLeft" -> fp.getMiddleLeft();
            case "ringLeft" -> fp.getRingLeft();
            case "littleLeft" -> fp.getLittleLeft();
            case "thumbRight" -> fp.getThumbRight();
            case "indexRight" -> fp.getIndexRight();
            case "middleRight" -> fp.getMiddleRight();
            case "ringRight" -> fp.getRingRight();
            case "littleRight" -> fp.getLittleRight();
            default -> null;
        };
    }

    private double round(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    // List Persons
    public List<Map<String, Object>> listPersons(ObjectId tenantId) {

        List<Person> persons = personRepo.findByTenantId(tenantId);
        List<Map<String, Object>> out = new ArrayList<>();

        for (Person p : persons) {

            Map<String, Object> oficina = null;
            String oficinaIdStr  = p.getOficinaId();

            if (oficinaIdStr != null && !oficinaIdStr.isBlank()) {
                ObjectId oficinaId = null;

                try {
                    oficinaId = new ObjectId(oficinaIdStr);
                } catch (IllegalArgumentException ex) {
                    log.warn("officeId inválido en persona {}: {}", p.getId(), oficinaIdStr);
                }

                if (oficinaId != null) {

                    Optional<Office> opt =
                            officeRepo.findByTenantIdAndId(tenantId, oficinaId);

                    if (opt.isPresent()) {

                        Office o = opt.get();
                        oficina = new LinkedHashMap<>();

                        oficina.put("id",          o.getId() != null ? o.getId().toHexString() : null);
                        oficina.put("nombre",      o.getName());
                        oficina.put("direccion",   o.getAddress());
                        oficina.put("paisId",      o.getCountryId());
                        oficina.put("estadoId",    o.getStateId());
                        oficina.put("municipioId", o.getMunicipalityId());
                    }
                }
            }

            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id",               p.getId() != null ? p.getId().toHexString() : null);
            dto.put("curp",             p.getCurp());
            dto.put("nombres",          p.getName());
            dto.put("primerApellido",   p.getPrimerApellido());
            dto.put("segundoApellido",  p.getSegundoApellido());
            dto.put("fechaNacimiento",  p.getFechaNacimiento() != null ? p.getFechaNacimiento().toString() : null);
            dto.put("sexo",             p.getSexo());
            dto.put("nacionalidad",     p.getNacionalidad());
            dto.put("direccion",        p.getDireccion());
            dto.put("oficina",          oficina);
            dto.put("facePhotoPath",    p.getFacePhotoPath());

            out.add(dto);

        }

        return out;
    }

    public Map<String, Object> toPersonLegacy(Person p) {

        Map<String, Object> out = new LinkedHashMap<>();

        // id's básicos
        out.put("id",       p.getId() != null ? p.getId().toHexString() : null);
        out.put("tenantId", p.getTenantId() != null ? p.getTenantId().toHexString() : null);

        // datos biográficos
        out.put("curp",            p.getCurp());
        out.put("name",            p.getName());
        out.put("primerApellido",  p.getPrimerApellido());
        out.put("segundoApellido", p.getSegundoApellido());
        out.put("fechaNacimiento",
                p.getFechaNacimiento() != null ? p.getFechaNacimiento().toString() : null);
        out.put("sexo",           p.getSexo());
        out.put("nacionalidad",   p.getNacionalidad());
        out.put("direccion",      p.getDireccion());

        // oficina embebida
        Map<String, Object> oficina = null;
        if (p.getOficinaId() != null && p.getTenantId() != null) {
            Office off = officeRepo
                    .findByTenantIdAndId(p.getTenantId(), p.getTenantId())
                    .orElse(null);

            if (off != null) {
                oficina = new LinkedHashMap<>();
                oficina.put("id",         off.getId() != null ? off.getId().toHexString() : null);
                oficina.put("nombre",     off.getName());
                oficina.put("direccion",  off.getAddress());
                oficina.put("paisId",     off.getCountryId());
                oficina.put("estadoId",   off.getStateId());
                oficina.put("municipioId", off.getMunicipalityId());
            }
        }
        out.put("oficina", oficina);

        out.put("facePhotoPath", p.getFacePhotoPath());

        return out;
    }

    // List One Person
    public Optional<Person> findPersonByCurp(ObjectId tenantId, String curp) {

        if (curp == null) return Optional.empty();

        return personRepo.findByTenantIdAndCurp(tenantId, curp.trim().toUpperCase());
    }


    public Optional<Person> findPerson(ObjectId tenantId, String curp) {
        return personRepo.findByTenantIdAndCurp(tenantId, curp);
    }

    @Transactional
    public Person upsertBiographic(ObjectId tenantId,
                                   String curp,
                                   String nombres,
                                   String primerApellido,
                                   String segundoApellido,
                                   LocalDate fechaNacimiento,
                                   String sexo,
                                   String nacionalidad,
                                   String direccion,
                                   String oficinaId) {

        if (curp == null || curp.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "curp_required");
        }

        String curpNorm = curp.trim().toUpperCase();

        // Validar Oficina
        if (oficinaId != null && !oficinaId.isBlank()) {

            ObjectId officeObjectId;
            try {
                officeObjectId = new ObjectId(oficinaId);
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Oficina_id_invalido"
                );
            }

            officeRepo.findByTenantIdAndId(tenantId, officeObjectId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "oficina_no_encontrada"
                    ));

        }

        // Buscar o crear persona por CURP
        Person p = personRepo.findByTenantIdAndCurp(tenantId, curpNorm)
                .orElseGet(() -> Person.builder()
                        .tenantId(tenantId)
                        .curp(curpNorm)
                        .build());

        // Campos biograficos
        String nombresNorm         = normalize(nombres);
        String primerApNorm        = normalize(primerApellido);
        String segundoApNorm       = normalize(segundoApellido);
        String sexoNorm            = normalize(sexo);
        String nacionalidadNorm    = normalize(nacionalidad);
        String direccionNorm       = normalize(direccion);

        // Se arma el nombre completo
        String fullName = buildFullName(nombresNorm, primerApNorm, segundoApNorm);

        p.setName(fullName);

        p.setName(nombresNorm);
        p.setPrimerApellido(primerApNorm);
        p.setSegundoApellido(segundoApNorm);

        p.setFechaNacimiento(fechaNacimiento);
        p.setSexo(sexoNorm);
        p.setNacionalidad(nacionalidadNorm);
        p.setDireccion(direccionNorm);

        // Se guarda el seq de la oficina
        p.setOficinaId(oficinaId);

        return personRepo.save(p);
    }

    // Devuelve null si esta vacio
    private String normalize(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    private String buildFullName(String nombres, String a1, String a2) {
        String s = String.join(" ",
                safe(nombres), safe(a1), safe(a2));
        // normaliza espacios
        return s.replaceAll("\\s+", " ").trim();
    }

    private String safe(String v) {
        return (v == null) ? "" : v.trim();
    }

}
