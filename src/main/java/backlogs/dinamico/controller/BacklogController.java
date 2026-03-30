package backlogs.dinamico.controller;

import backlogs.dinamico.model.Backlog;
import backlogs.dinamico.service.BacklogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/backlogs")
@RequiredArgsConstructor
public class BacklogController {
    
    private final BacklogService backlogService;
    
    @GetMapping
    public ResponseEntity<List<Backlog>> obtenerTodos() {
        return ResponseEntity.ok(backlogService.obtenerTodos());
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Backlog> obtenerPorId(@PathVariable String id) {
        return backlogService.obtenerPorId(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping
    public ResponseEntity<Backlog> crear(@RequestBody Backlog backlog) {
        Backlog creado = backlogService.crear(backlog);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<Backlog> actualizar(@PathVariable String id, @RequestBody Backlog backlog) {
        try {
            Backlog actualizado = backlogService.actualizar(id, backlog);
            return ResponseEntity.ok(actualizado);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        backlogService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/estado/{estado}")
    public ResponseEntity<List<Backlog>> buscarPorEstado(@PathVariable String estado) {
        return ResponseEntity.ok(backlogService.buscarPorEstado(estado));
    }
    
    @GetMapping("/prioridad/{prioridad}")
    public ResponseEntity<List<Backlog>> buscarPorPrioridad(@PathVariable String prioridad) {
        return ResponseEntity.ok(backlogService.buscarPorPrioridad(prioridad));
    }
}
