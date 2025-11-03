package backlogs.dinamico.error;

import backlogs.dinamico.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.CONFLICT;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("not_found", ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException ex, HttpServletRequest req) {
        return ResponseEntity.status(CONFLICT)
                .body(ApiResponse.error("conflict", ex.getMessage(), req.getRequestURI()));
    }

//    @ExceptionHandler(DuplicateKeyException.class)
//    public ResponseEntity<ApiResponse<Void>> handleDup(DuplicateKeyException ex, HttpServletRequest req) {
//        String msg = MongoErrors.buildDuplicateMessage(ex, "value");
//        return ResponseEntity.status(CONFLICT)
//                .body(ApiResponse.error("Conflict", msg, req.getRequestURI()));
//    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("Validation_error", details, req.getRequestURI()));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            IllegalArgumentException.class,
            ConstraintViolationException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex, HttpServletRequest req) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("bad_request", ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(AccessDeniedException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("forbidden", "You don't have permission to perform this action", req.getRequestURI()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleRSE(ResponseStatusException ex, HttpServletRequest req) {
        int sc = ex.getStatusCode().value(); // HttpStatusCode -> int
        String code = switch (sc) {
            case 404 -> "not_found";
            case 409 -> "conflict";
            case 400 -> "bad_request";
            case 403 -> "forbidden";
            case 401 -> "unauthorized";
            default -> "error";
        };
        return ResponseEntity.status(sc)
                .body(ApiResponse.error(code, ex.getReason(), req.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest req) {
        // Log completo en tu logger; aquí devolvemos mensaje genérico
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("internal_error", "Unexpected error", req.getRequestURI()));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(DuplicateKeyException ex, HttpServletRequest req) {
        String msg = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();

        // Mensaje “bonito” cuando el índice es el de roles
        if (msg != null && msg.contains("roles") && msg.contains("ux_role_tenant_code")) {
            return ResponseEntity.status(CONFLICT)
                    .body(ApiResponse.error("role_already_exists",
                            "Ya existe un rol con ese código en esta organización.",
                            req.getRequestURI()));
        }
        return ResponseEntity.status(CONFLICT)
                .body(ApiResponse.error("write_conflict", msg, req.getRequestURI()));
    }

}
