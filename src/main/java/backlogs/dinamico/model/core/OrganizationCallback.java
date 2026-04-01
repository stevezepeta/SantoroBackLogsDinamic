package backlogs.dinamico.model.core;

import com.mongodb.lang.Nullable;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Component
public class OrganizationCallback implements BeforeConvertCallback<Organization> {

    @Override
    public Organization onBeforeConvert(Organization entity,
                                        @Nullable String collection) {

        if (entity == null) return null;

        if (StringUtils.hasText(entity.getCode())) {
            String norm = entity.getCode().trim()
                    .toUpperCase(Locale.ROOT)
                    .replaceAll("[^A-Z0-9]+", "_")
                    .replaceAll("^_+|_+$", "");
            if (norm.length() > 32) norm = norm.substring(0, 32);
            if (StringUtils.hasText(norm)) entity.setCode(norm);
        }

        if (StringUtils.hasText(entity.getSlug())) {
            entity.setSlug(slugify(entity.getSlug()));
        }

        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus("active");
        }
        return entity;
    }

    private String slugify(String s) {
        String out = s == null ? "" : s.trim().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "-")
                .replaceAll("[^a-z0-9-]", "")
                .replaceAll("^-+|-+$", "");
        if (out.length() < 3) out = (out + "-org").replaceAll("^-+|-+$", "");
        return out;
    }

}
