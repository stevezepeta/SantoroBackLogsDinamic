package backlogs.dinamico.infra.web;

import org.bson.types.ObjectId;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class ObjectIdConverter implements Converter<String, ObjectId> {
  @Override
  public ObjectId convert(String source) {
    if (source == null || source.isBlank()) return null;
    return new ObjectId(source.trim());
  }
}
