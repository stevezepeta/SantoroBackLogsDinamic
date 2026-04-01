package backlogs.dinamico.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.bson.types.ObjectId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class JacksonObjectIdConfig {

  @Bean
  public Module objectIdModule() {
    SimpleModule m = new SimpleModule();
    m.addSerializer(ObjectId.class, ToStringSerializer.instance);
    m.addDeserializer(ObjectId.class, new JsonDeserializer<ObjectId>() {
      @Override
      public ObjectId deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String v = p.getValueAsString();
        return (v == null || v.isBlank()) ? null : new ObjectId(v);
      }
    });
    return m;
  }
}
