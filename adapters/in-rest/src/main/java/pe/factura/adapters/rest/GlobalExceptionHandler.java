package pe.factura.adapters.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pe.factura.domain.DomainException;

import java.util.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> dominio(DomainException e) {
        HttpStatus st = switch (e.codigo()) {
            case "NO_ENCONTRADO", "SIN_CDR" -> HttpStatus.NOT_FOUND;
            case "DUPLICADO", "ESTADO_NO_ENVIABLE" -> HttpStatus.CONFLICT;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ResponseEntity.status(st).body(ApiResponse.error(e.codigo(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> validacion(MethodArgumentNotValidException e) {
        Map<String, List<String>> errores = new TreeMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> errores.computeIfAbsent(f.getField(), k -> new ArrayList<>()).add(f.getDefaultMessage()));
        return ResponseEntity.unprocessableEntity().body(ApiResponse.validacion(errores));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> jsonInvalido(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error("JSON_INVALIDO", "El cuerpo de la petición no es JSON válido"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> interno(Exception e) {
        String traceId = UUID.randomUUID().toString();
        log.error("Error interno trace_id={}", traceId, e);
        return ResponseEntity.internalServerError().body(ApiResponse.error("INTERNO", "Error interno. trace_id=" + traceId));
    }
}
