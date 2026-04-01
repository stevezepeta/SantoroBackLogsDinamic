package backlogs.dinamico.tenant;

import backlogs.dinamico.model.core.Organization;
import backlogs.dinamico.repository.core.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class TenantResolver {

    private final OrganizationRepository orgRepo;

    public ObjectId resolverForLing(ObjectId xTenant, String orgSlug) {
        if (xTenant != null) return xTenant;
        if (StringUtils.hasText(orgSlug)) {
            return orgRepo.findBySlugIgnoreCase(orgSlug.trim())
                    .map(Organization::getId)
                    .orElse(null);
        }
        return null;
    }

}
