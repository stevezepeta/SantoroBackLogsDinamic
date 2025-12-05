package backlogs.dinamico.api.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class PersonCreateReq {

    private String curp;
    private String name;
    private String primerApellido;
    private String segundoApellido;
    private LocalDate fechaNacimiento;
    private String sexo;
    private String nacionalidad;
    private String direccion;

    private String oficinaId;

    // Opcional el uso de este campo
    private String facePhotoPath;

}
