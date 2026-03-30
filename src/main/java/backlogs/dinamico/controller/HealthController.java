package backlogs.dinamico.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {
    
    private final MongoTemplate mongoTemplate;
    
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());
        
        try {
            // Verificar conexión a MongoDB
            String dbName = mongoTemplate.getDb().getName();
            var collections = mongoTemplate.getCollectionNames();
            
            health.put("mongodb", Map.of(
                "status", "CONNECTED",
                "database", dbName,
                "collections", collections
            ));
        } catch (Exception e) {
            health.put("mongodb", Map.of(
                "status", "ERROR",
                "error", e.getMessage()
            ));
        }
        
        return ResponseEntity.ok(health);
    }
}
