package backlogs.dinamico.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "backlogs")
public class Backlog {
    
    @Id
    private String id;
    
    private String titulo;
    private String descripcion;
    private String prioridad; // ALTA, MEDIA, BAJA
    private String estado; // POR_HACER, EN_PROGRESO, COMPLETADO
    private String asignadoA;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
    
    public Backlog(String titulo, String descripcion, String prioridad, String estado, String asignadoA) {
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.prioridad = prioridad;
        this.estado = estado;
        this.asignadoA = asignadoA;
        this.fechaCreacion = LocalDateTime.now();
        this.fechaActualizacion = LocalDateTime.now();
    }
}
