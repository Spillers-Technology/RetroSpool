package io.retrospool.web;

import io.retrospool.secrets.SecretStorageUnavailableException;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps admin-API domain errors onto HTTP status codes. */
@RestControllerAdvice(basePackages = "io.retrospool.web")
public class AdminExceptionAdvice {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> notFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found", "message", msg(e)));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "conflict", "message", msg(e)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "bad_request", "message", msg(e)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .orElse("Request validation failed.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "bad_request", "message", detail));
    }

    // D-012/D-023: mirrors IntakeExceptionAdvice — no encryption key configured yet.
    @ExceptionHandler(SecretStorageUnavailableException.class)
    public ResponseEntity<Map<String, String>> secretsDisabled(SecretStorageUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "secret_storage_unavailable", "message", msg(e)));
    }

    private static String msg(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}
