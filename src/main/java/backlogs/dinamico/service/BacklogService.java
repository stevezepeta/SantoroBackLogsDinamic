package backlogs.dinamico.service;

import backlogs.dinamico.model.Backlog;
import backlogs.dinamico.repository.BacklogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BacklogService {
    
    private final BacklogRepository backlogRepository;
    
    public List<Backlog> obtenerTodos() {
        return backlogRepository.findAll();
    }
    
    public Optional<Backlog> obtenerPorId(String id) {
        return backlogRepository.findById(id);
    }
    
    public Backlog crear(Backlog backlog) {
        backlog.setFechaCreacion(LocalDateTime.now());
        backlog.setFechaActualizacion(LocalDateTime.now());
        return backlogRepository.save(backlog);
    }
    
    public Backlog actualizar(String id, Backlog backlog) {
        return backlogRepository.findById(id)
            .map(existente -> {
                existente.setTitulo(backlog.getTitulo());
                existente.setDescripcion(backlog.getDescripcion());
                existente.setPrioridad(backlog.getPrioridad());
                existente.setEstado(backlog.getEstado());
                existente.setAsignadoA(backlog.getAsignadoA());
                existente.setFechaActualizacion(LocalDateTime.now());
                return backlogRepository.save(existente);
            })
            .orElseThrow(() -> new RuntimeException("Backlog no encontrado: " + id));
    }
    
    public void eliminar(String id) {
        backlogRepository.deleteById(id);
    }
    
    public List<Backlog> buscarPorEstado(String estado) {
        return backlogRepository.findByEstado(estado);
    }
    
    public List<Backlog> buscarPorPrioridad(String prioridad) {
        return backlogRepository.findByPrioridad(prioridad);
    }
}
