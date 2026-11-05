package mx.buap.veterinaria.controller;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;
@RestControllerAdvice
public class ApiExceptionHandler {
 @ExceptionHandler(ResponseStatusException.class)
 public ResponseEntity<?> estado(ResponseStatusException ex){return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message",ex.getReason()==null?"No se pudo completar la operación.":ex.getReason()));}
 @ExceptionHandler(MethodArgumentNotValidException.class)
 public ResponseEntity<?> datos(MethodArgumentNotValidException ex){return ResponseEntity.badRequest().body(Map.of("message","Revise los campos: "+ex.getBindingResult().getFieldErrors().stream().map(e -> e.getField()+": "+e.getDefaultMessage()).distinct().collect(java.util.stream.Collectors.joining("; "))));}
 @ExceptionHandler(DataIntegrityViolationException.class)
 public ResponseEntity<?> integridad(DataIntegrityViolationException ex){return ResponseEntity.status(409).body(Map.of("message","El registro ya existe o sus datos no cumplen las restricciones. Revise usuario, código de barras y registros vinculados."));}
}
