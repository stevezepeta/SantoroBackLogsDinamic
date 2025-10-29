package backlogs.dinamico.service.biometric;

import backlogs.dinamico.config.BiometricProps;
import backlogs.dinamico.model.biometric.FingerPrint;
import backlogs.dinamico.model.biometric.Person;
import backlogs.dinamico.repository.biometric.FingerPrintRepository;
import backlogs.dinamico.repository.biometric.PersonRepository;
import backlogs.dinamico.tenant.TenantContext;
import com.machinezoo.sourceafis.FingerprintImage;
import com.machinezoo.sourceafis.FingerprintImageOptions;
import com.machinezoo.sourceafis.FingerprintMatcher;
import com.machinezoo.sourceafis.FingerprintTemplate;
import lombok.RequiredArgsConstructor;

import lombok.Value;
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
import java.util.Map;
import java.util.Optional;

import static java.lang.Math.round;

@Service
@RequiredArgsConstructor
public class BiometricService {

    private final PersonRepository personRepo;
    private final FingerPrintRepository fpRepo;
    private final BiometricProps props;

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

    public Optional<Person> findPerson(ObjectId tenantId, String curp) {
        return personRepo.findByTenantIdAndCurp(tenantId, curp);
    }

    @Transactional
    public Person upsertBiographic(ObjectId tenantId,
                                   String curp,
                                   String nombres,
                                   String primerApellido,
                                   String segundoApellido) {

        // Se arma el nombre completo
        String full = buildFullName(nombres, primerApellido, segundoApellido);

        Person p = personRepo.findByTenantIdAndCurp(tenantId, curp)
                .orElseGet(() -> Person.builder()
                        .tenantId(tenantId)
                        .curp(curp)
                        .build());

        p.setName(full);
        return personRepo.save(p);

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
