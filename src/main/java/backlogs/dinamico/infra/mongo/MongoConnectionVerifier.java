package backlogs.dinamico.infra.mongo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import org.bson.Document;

@Slf4j
@Component
@Profile({"dev","prod"})
public class MongoConnectionVerifier implements CommandLineRunner {

    private final MongoTemplate mongoTemplate;

    public MongoConnectionVerifier(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            Document result = mongoTemplate.executeCommand(new Document("ping", 1));
            String dbName = mongoTemplate.getDb().getName();
            log.info("MongoDB ping OK. DB actual: {} | Respuesta: {}", dbName, result.toJson());
        } catch (Exception e) {
            log.error("No se pudo conectar a MongoDB en el arranque.", e);

            throw new IllegalStateException("MongoDB no disponible al iniciar", e);
        }
    }

}
