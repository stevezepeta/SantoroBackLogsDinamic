package backlogs.dinamico.api;

import com.mongodb.MongoWriteException;
import org.bson.types.ObjectId;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler({
      MethodArgumentTypeMismatchException.class,
      ConversionFailedException.class,
      IllegalArgumentException.class
  })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, Object> handleTypeMismatch(Exception ex) {
    if (ex instanceof MethodArgumentTypeMismatchException m
        && m.getRequiredType() == ObjectId.class) {
      return Map.of("error", "invalid_object_id", "message", "El id no es un ObjectId válido");
    }
    if (ex instanceof ConversionFailedException c
        && c.getTargetType() != null
        && c.getTargetType().getType() == ObjectId.class) {
      return Map.of("error", "invalid_object_id", "message", "El id no es un ObjectId válido");
    }
    if (ex instanceof IllegalArgumentException) { // lanzada por new ObjectId(...)
      return Map.of("error", "invalid_object_id", "message", "El id no es un ObjectId válido");
    }
    return Map.of("error", "bad_request", "message", ex.getMessage());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, Object> handleNotReadable(HttpMessageNotReadableException ex) {
    return Map.of("error", "invalid_json", "message", "Body JSON inválido o tipos incorrectos");
  }

  @ExceptionHandler(MongoWriteException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public Map<String, Object> handleMongoWrite(MongoWriteException ex) {
    var msg = ex.getError() != null ? ex.getError().getMessage() : ex.getMessage();
    return Map.of("error", "write_conflict", "message", msg);
  }
}
