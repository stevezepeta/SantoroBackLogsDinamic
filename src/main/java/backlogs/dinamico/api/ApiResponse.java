package backlogs.dinamico.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean ok,
        String code,
        String message,
        String path,
        Instant timestamp,
        T data
) {

    // === ÉXITO (genérico) ===
    public static <T> ApiResponse<T> success(String code, T data) {
        return new ApiResponse<>(true, code, null, null, Instant.now(), data);
    }

    /** Éxito con código, message y data, sin path */
    public static <T> ApiResponse<T> success(String code, String message, T data) {
        return new ApiResponse<>(true, code, message, null, Instant.now(), data);
    }

    public static <T> ApiResponse<T> success(String code, String message, String path, T data) {
        return new ApiResponse<>(true, code, message, path, Instant.now(), data);
    }

    public static <T> ApiResponse<T> ok(String message, String path, T data) {
        return new ApiResponse<>(true, "ok", message, path, Instant.now(), data);
    }

    public static <T> ApiResponse<T> created(String message, String path, T data) {
        return new ApiResponse<>(true, "created", message, path, Instant.now(), data);
    }

    public static ApiResponse<Void> deleted(String message, String path) {
        return new ApiResponse<>(true, "deleted", message, path, Instant.now(), null);
    }

    // === ERROR ===
    public static <T> ApiResponse<T> error(String code, String message, String path) {
        return new ApiResponse<>(false, code, message, path, Instant.now(), null);
    }

}
