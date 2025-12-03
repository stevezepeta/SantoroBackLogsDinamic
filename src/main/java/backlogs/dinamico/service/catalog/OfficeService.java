package backlogs.dinamico.service.catalog;

import backlogs.dinamico.api.dto.OfficeCreatedRequest;
import backlogs.dinamico.api.dto.OfficeMapper;
import backlogs.dinamico.api.dto.OfficeResponse;
import backlogs.dinamico.api.dto.OfficeUpdateRequest;
import backlogs.dinamico.model.catalog.Office;
import backlogs.dinamico.repository.catalog.OfficeRepository;
import backlogs.dinamico.service.support.SequenceService;
import backlogs.dinamico.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class OfficeService {

  private final OfficeRepository repo;
  private final SequenceService sequences;

  private ObjectId requireTenant() {
    var t = TenantContext.getTenantId();
    if (t == null) throw new ResponseStatusException(BAD_REQUEST, "missing_tenant");
    return t;
  }

  public OfficeResponse create(OfficeCreatedRequest req) {
    var tenantId = requireTenant();

    if (!StringUtils.hasText(req.getName()))
      throw new ResponseStatusException(BAD_REQUEST, "nombre_required");
    if (!StringUtils.hasText(req.getAddress()))
      throw new ResponseStatusException(BAD_REQUEST, "direccion_required");

    long seq = sequences.next(SequenceService.officeKey(tenantId));

    Office.GeoPoint geo = null;
    if (req.getGeo() != null) {
      geo = new Office.GeoPoint(
              req.getGeo().getType(),
              req.getGeo().getCoordinates()
      );
    }

    Office entity = Office.builder()
            .tenantId(tenantId)
            .seq(seq)
            .name(req.getName().trim())
            .address(req.getAddress().trim())
            .countryId(StringUtils.hasText(req.getCountryId()) ? req.getCountryId().trim() : null)
            .stateId(StringUtils.hasText(req.getStateId()) ? req.getStateId().trim() : null)
            .municipalityId(StringUtils.hasText(req.getMunicipalityId()) ? req.getMunicipalityId().trim() : null)
            .status(StringUtils.hasText(req.getStatus()) ? req.getStatus().trim() : "active")
            .geo(geo)
            .build();

    entity = repo.save(entity);
    return OfficeMapper.toResponse(entity);
  }

  public List<OfficeResponse> listAllForOrg() {
    var tenantId = requireTenant();
    return repo.findByTenantIdOrderBySeqAsc(tenantId)
            .stream()
            .map(OfficeMapper::toResponse)
            .toList();
  }

  public OfficeResponse getBySeq(Long id) {
    var tenantId = requireTenant();
    var office = repo.findByTenantIdAndSeq(tenantId, id)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "office_not_found"));
    return OfficeMapper.toResponse(office);
  }

  public OfficeResponse updateBySeq(Long id, OfficeUpdateRequest patch) {
    var tenantId = requireTenant();

    var current = repo.findByTenantIdAndSeq(tenantId, id)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "office_not_found"));

    if (StringUtils.hasText(patch.getName())) current.setName(patch.getName().trim());
    if (StringUtils.hasText(patch.getAddress())) current.setAddress(patch.getAddress().trim());
    if (patch.getCountry() != null) current.setCountryId(StringUtils.hasText(patch.getCountry()) ? patch.getCountry().trim() : null);
    if (patch.getStateId() != null) current.setStateId(StringUtils.hasText(patch.getStateId()) ? patch.getStateId().trim() : null);
    if (patch.getMunicipalityId() != null) current.setMunicipalityId(StringUtils.hasText(patch.getMunicipalityId()) ? patch.getMunicipalityId().trim() : null);
    if (StringUtils.hasText(patch.getStatus())) current.setStatus(patch.getStatus().trim());

    current = repo.save(current);
    return OfficeMapper.toResponse(current);
  }

  public void deleteBySeq(Long id) {
    var tenantId = requireTenant();
    if (!repo.existsByTenantIdAndSeq(tenantId, id))
      throw new ResponseStatusException(NOT_FOUND, "office_not_found");
    repo.deleteByTenantIdAndSeq(tenantId, id);
  }


}
