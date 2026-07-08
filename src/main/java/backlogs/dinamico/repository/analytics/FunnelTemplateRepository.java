package backlogs.dinamico.repository.analytics;

import backlogs.dinamico.model.analytics.FunnelTemplate;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio para acceder a las configuraciones de embudos almacenadas en MongoDB.
 * 
 * Permite gestionar templates de funnel de forma dinámica sin requerir cambios en código.
 */
@Repository
public interface FunnelTemplateRepository extends MongoRepository<FunnelTemplate, ObjectId> {

    /**
     * Busca un template de funnel por nombre de sistema.
     * 
     * @param systemName Nombre del sistema (ej: TRUSTVALUE, CITA_GUYANA)
     * @return Optional con el template si existe
     */
    Optional<FunnelTemplate> findBySystemName(String systemName);

    /**
     * Busca un template de funnel activo por nombre de sistema.
     * 
     * @param systemName Nombre del sistema
     * @param active Estado de activación
     * @return Optional con el template si existe y está activo
     */
    Optional<FunnelTemplate> findBySystemNameAndActive(String systemName, Boolean active);

    /**
     * Lista todos los templates activos.
     * 
     * @param active Estado de activación
     * @return Lista de templates activos
     */
    List<FunnelTemplate> findByActive(Boolean active);

    /**
     * Verifica si existe un template para un sistema específico.
     * 
     * @param systemName Nombre del sistema
     * @return true si existe, false en caso contrario
     */
    boolean existsBySystemName(String systemName);

    /**
     * Verifica si existe un template activo para un sistema específico.
     * 
     * @param systemName Nombre del sistema
     * @param active Estado de activación
     * @return true si existe y está activo
     */
    boolean existsBySystemNameAndActive(String systemName, Boolean active);
}

