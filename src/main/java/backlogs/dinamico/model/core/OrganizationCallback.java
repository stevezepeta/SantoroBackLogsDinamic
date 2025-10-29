package backlogs.dinamico.model.core;

import com.mongodb.lang.Nullable;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OrganizationCallback implements BeforeConvertCallback<Organization> {

    @Override
    public Organization onBeforeConvert(Organization entity, @Nullable String collection) {
        if (entity == null) return null;

        if (StringUtils.hasText(entity.getSlug())) {
            entity.setSlug(slugify(entity.getSlug()));
        }
        if (StringUtils.hasText(entity.getCode())) {
            entity.setCode(entity.getCode().trim().toLowerCase());
        }
        if (StringUtils.hasText(entity.getDomain())) {
            entity.setDomain(entity.getDomain().trim().toLowerCase());
        }
        return entity;
    }

    private String slugify(String s) {
        String out = s.trim().toLowerCase()
                .replaceAll("\\s+", "-")
                .replaceAll("[^a-z0-9-]", "");

        if (out.length() < 3) out = out + "-org";
        return out;
    }

}
