package io.retrospool.api;

import io.retrospool.secrets.SecretStorageUnavailableException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Error mapping for the public intake surface (mirrors {@code AdminExceptionAdvice}, but
 * scoped to {@code io.retrospool.api} so admin and public surfaces stay independent).
 * Every body carries a {@code message} the SPA renders directly.
 */
@RestControllerAdvice(basePackages = "io.retrospool.api")
public class IntakeExceptionAdvice {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return body(HttpStatus.BAD_REQUEST, "bad_request", msg(e));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .orElse("Request validation failed.");
        return body(HttpStatus.BAD_REQUEST, "bad_request", detail);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> tooLarge(MaxUploadSizeExceededException e) {
        return body(HttpStatus.PAYLOAD_TOO_LARGE, "too_large",
                "That file is too large to be a session profile.");
    }

    @ExceptionHandler(SecretStorageUnavailableException.class)
    public ResponseEntity<Map<String, String>> secretsDisabled(SecretStorageUnavailableException e) {
        return body(HttpStatus.SERVICE_UNAVAILABLE, "secret_storage_unavailable", msg(e));
    }

    private static ResponseEntity<Map<String, String>> body(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(Map.of("error", error, "message", message));
    }

    private static String msg(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}
