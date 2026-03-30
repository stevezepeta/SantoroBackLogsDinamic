package backlogs.dinamico.repository;

import backlogs.dinamico.model.Backlog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BacklogRepository extends MongoRepository<Backlog, String> {
    
    List<Backlog> findByEstado(String estado);
    List<Backlog> findByPrioridad(String prioridad);
    List<Backlog> findByAsignadoA(String asignadoA);
}
