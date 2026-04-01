package backlogs.dinamico.error;

import backlogs.dinamico.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.CONFLICT;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<?> handleNoResource(
            org.springframework.web.servlet.resource.NoResourceFoundException ex,
            HttpServletRequest req
    ) {
        return ApiResponse.error("not_found", "Recurso no encontrado", req.getRequestURI());
    }

    @ExceptionHandler(org.springframework.web.servlet.NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<?> handleNoHandler(
            org.springframework.web.servlet.NoHandlerFoundException ex,
            HttpServletRequest req
    ) {
        return ApiResponse.error("not_found", "Ruta no encontrada", req.getRequestURI());
    }

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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("validation_error", details, req.getRequestURI()));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            IllegalArgumentException.class,
            ConstraintViolationException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex, HttpServletRequest req) {
        String msg = (ex.getMessage() == null || ex.getMessage().isBlank())
                ? "Bad request"
                : ex.getMessage();

        return ResponseEntity.badRequest()
                .body(ApiResponse.error("bad_request", msg, req.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(AccessDeniedException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("forbidden", "You don't have permission to perform this action", req.getRequestURI()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleRSE(ResponseStatusException ex, HttpServletRequest req) {
        int sc = ex.getStatusCode().value();
        String code = switch (sc) {
            case 404 -> "not_found";
            case 409 -> "conflict";
            case 400 -> "bad_request";
            case 403 -> "forbidden";
            case 401 -> "unauthorized";
            default -> "error";
        };

        String msg = (ex.getReason() == null || ex.getReason().isBlank())
                ? ex.getMessage()
                : ex.getReason();

        return ResponseEntity.status(sc)
                .body(ApiResponse.error(code, msg, req.getRequestURI()));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(DuplicateKeyException ex, HttpServletRequest req) {
        String msg = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();

        if (msg != null && msg.contains("roles") && msg.contains("ux_role_tenant_code")) {
            return ResponseEntity.status(CONFLICT)
                    .body(ApiResponse.error("role_already_exists",
                            "Ya existe un rol con ese código en esta organización.",
                            req.getRequestURI()));
        }
        return ResponseEntity.status(CONFLICT)
                .body(ApiResponse.error("write_conflict", msg, req.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest req) {
        String errorId = UUID.randomUUID().toString();

        // Log completo (stacktrace incluido) + contexto mínimo
        log.error("UNEXPECTED_ERROR errorId={} method={} uri={} query={}",
                errorId,
                req.getMethod(),
                req.getRequestURI(),
                req.getQueryString(),
                ex
        );

        // Respuesta genérica pero con errorId para que lo busques en logs
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("internal_error", "Unexpected error. errorId=" + errorId, req.getRequestURI()));
    }
}
