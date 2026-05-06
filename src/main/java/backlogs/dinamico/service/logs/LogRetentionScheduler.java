package backlogs.dinamico.service.logs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Ejecuta la retención de logs una vez al día a las 3:00 AM hora México.
 * Hora de madrugada para minimizar impacto en el rendimiento.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogRetentionScheduler {

    private final LogRetentionService retentionService;

    @Scheduled(cron = "0 0 3 * * *", zone = "America/Mexico_City")
    public void runDailyRetention() {
        log.info("[RetentionScheduler] Iniciando limpieza diaria de logs...");
        try {
            long totalDeleted = retentionService.runRetention();
            log.info("[RetentionScheduler] Limpieza completada — {} logs eliminados.", totalDeleted);
        } catch (Exception e) {
            log.error("[RetentionScheduler] Error en limpieza: {}", e.getMessage());
        }
    }
}