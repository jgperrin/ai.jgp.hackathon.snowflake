package ai.jgp.hackathon.snowflake.web;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import ai.jgp.hackathon.snowflake.connection.ConnectionModel;

/** Every API error reaches the browser as {@code {"message": ...}} so the UI can show it. */
@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ConnectionModel.NoConnectionException.class)
    ResponseEntity<Map<String, String>> noConnection(ConnectionModel.NoConnectionException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage(), "code", "NO_CONNECTION"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    /** Snowflake said no (privileges, missing object, warehouse suspended...): pass its reason on. */
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Map<String, String>> snowflake(DataAccessException e) {
        log.warn("Snowflake call failed", e);
        String reason = e.getMostSpecificCause().getMessage();
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", "Snowflake: " + reason));
    }
}
