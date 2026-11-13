package com.cafeoccidente.backend.common.exception;

import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Traduce excepciones de negocio a respuestas HTTP consistentes. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    static final String USER_DISABLED_CODE = "USER_DISABLED";
    static final String USER_DISABLED_MESSAGE = "Su usuario está desactivado. Comuníquese con el Administrador.";
    static final String UNREADABLE_MESSAGE = "Los datos enviados no se pueden leer: revise los números y las fechas";
    static final String INTEGRITY_MESSAGE = "El dato choca con otro registro de la base";
    static final String TOO_LONG_MESSAGE = "Un dato es más largo de lo permitido";
    static final String BAD_PARAMS_MESSAGE = "La dirección trae parámetros que no se pueden leer";
    /** SQLSTATE de Postgres para un texto mas largo que su columna (string_data_right_truncation). */
    private static final String TOO_LONG = "22001";
    private static final Pattern CONSTRAINT = Pattern.compile("(uq|pk|fk|ck)_[a-z0-9_]+");
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApiException(ApiException ex) {
        Map<String, Object> response = body(ex.getStatus(), ex.getMessage());
        if (ex.getCode() != null) {
            response.put("code", ex.getCode());
        }
        return ResponseEntity.status(ex.getStatus()).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(body(HttpStatus.FORBIDDEN, "No tiene permisos para realizar esta accion"));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(body(HttpStatus.UNAUTHORIZED, "Usuario o contrasena invalidos"));
    }

    /** Usuario desactivado que digito bien su contrasena; el frontend lo distingue por "code". */
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<Map<String, Object>> handleDisabled(DisabledException ex) {
        Map<String, Object> response = body(HttpStatus.UNAUTHORIZED, USER_DISABLED_MESSAGE);
        response.put("code", USER_DISABLED_CODE);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /** Prueba del 11-nov (punto 4): dice que campos faltan o estan mal, con su nombre en pantalla. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));
        String fields = fieldErrors.keySet().stream().map(FieldLabels::of).distinct().sorted()
                .collect(Collectors.joining(", "));
        Map<String, Object> response = body(HttpStatus.BAD_REQUEST, "Falta o está mal: " + fields);
        response.put("fieldErrors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(body(HttpStatus.BAD_REQUEST, UNREADABLE_MESSAGE));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParameter(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest().body(body(HttpStatus.BAD_REQUEST, "Falta el dato " + ex.getParameterName()));
    }

    /** S8 (ZAP): Tomcat 11 no lee una consulta como "?=" (parametro sin nombre); es un 400, no un error del servidor. */
    @ExceptionHandler(InvalidParameterException.class)
    public ResponseEntity<Map<String, Object>> handleBadParameters(InvalidParameterException ex) {
        return ResponseEntity.badRequest().body(body(HttpStatus.BAD_REQUEST, BAD_PARAMS_MESSAGE));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(body(HttpStatus.BAD_REQUEST, "Valor no válido para " + ex.getName() + ": " + ex.getValue()));
    }

    /** Una regla de la base (unico, relacion, check): dice cual, por el nombre de la restriccion. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrity(DataIntegrityViolationException ex) {
        // S8 (ZAP): un texto mas largo que la columna es un dato mal digitado, no un choque con otro registro.
        if (ex.getMostSpecificCause() instanceof SQLException sql && TOO_LONG.equals(sql.getSQLState())) {
            return ResponseEntity.badRequest().body(body(HttpStatus.BAD_REQUEST, TOO_LONG_MESSAGE));
        }
        Matcher constraint = CONSTRAINT.matcher(String.valueOf(ex.getMostSpecificCause().getMessage()));
        String message = INTEGRITY_MESSAGE;
        if (constraint.find()) {
            String name = constraint.group();
            message = switch (name.substring(0, 3)) {
                case "uq_", "pk_" -> "Ya existe un registro con ese dato (" + name + ")";
                case "fk_" -> "El registro está relacionado con otros datos (" + name + ")";
                default -> "Un valor no cumple las reglas de la base (" + name + ")";
            };
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body(HttpStatus.CONFLICT, message));
    }

    /**
     * Lo no previsto: el detalle queda en el log con una referencia y la pantalla muestra esa referencia. Las
     * excepciones HTTP de Spring (404, 405...) conservan su estado.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        if (ex instanceof ErrorResponse error) {
            HttpStatus status = HttpStatus.valueOf(error.getStatusCode().value());
            String message = status == HttpStatus.NOT_FOUND ? "No se encontró lo que se pidió"
                    : "No se pudo atender la petición (" + status.value() + ")";
            return ResponseEntity.status(status).body(body(status, message));
        }
        String reference = UUID.randomUUID().toString().substring(0, 8);
        log.error("Error no previsto (ref. {})", reference, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body(HttpStatus.INTERNAL_SERVER_ERROR, "Error en el servidor (ref. " + reference + ")"));
    }

    private Map<String, Object> body(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return body;
    }
}
