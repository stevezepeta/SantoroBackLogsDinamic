package backlogs.dinamico.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

@ControllerAdvice
public class AuthErrorHandler {

    private Map<String, Object> simpleError(String msg) {

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("error", msg);

        return m;
    }


    // CREDENCIALES INVALIDAD
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(simpleError("Credenciales invalidas"));
    }

    // OTROS ERRORES DE AUTENTICACION
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", "Datos Invalidos");

        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.put(fe.getField(), fe.getDefaultMessage());
        }
        body.put("fields", fields);

        return ResponseEntity.badRequest().body(body);

    }

    // FALBACK 400 CUALQUIER ILLEGALARGUMENT
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArg(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(simpleError(ex.getMessage()));
    }


}
