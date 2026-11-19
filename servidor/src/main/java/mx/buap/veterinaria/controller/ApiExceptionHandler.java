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
 private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class);
 @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
 public ResponseEntity<?> cuerpo(Exception ex){return ResponseEntity.badRequest().body(Map.of("message","El contenido enviado no es válido. Revise formatos de números y fechas (AAAA-MM-DD)."));}
 @ExceptionHandler({org.springframework.web.bind.MissingServletRequestParameterException.class, org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
   org.springframework.web.multipart.support.MissingServletRequestPartException.class})
 public ResponseEntity<?> parametro(Exception ex){return ResponseEntity.badRequest().body(Map.of("message","Falta un dato o tiene un formato inválido."));}
 @ExceptionHandler(org.springframework.web.method.annotation.HandlerMethodValidationException.class)
 public ResponseEntity<?> validacion(Exception ex){return ResponseEntity.badRequest().body(Map.of("message","Revise los datos capturados."));}
 @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
 public ResponseEntity<?> tamano(Exception ex){return ResponseEntity.status(413).body(Map.of("message","El archivo supera el tamaño permitido (5 MB)."));}
 /** Errores de base de datos no previstos: se registran en el log y al cliente solo se le da un mensaje genérico (sin SQL). */
 @ExceptionHandler(org.springframework.dao.DataAccessException.class)
 public ResponseEntity<?> baseDatos(org.springframework.dao.DataAccessException ex){
  if(ex instanceof DataIntegrityViolationException d) return integridad(d);
  LOG.error("Error de base de datos no controlado: {}", ex.getClass().getSimpleName(), ex);
  return ResponseEntity.status(500).body(Map.of("message","Ocurrió un error interno. Intente de nuevo; si persiste, avise al administrador."));
 }
 @ExceptionHandler(DataIntegrityViolationException.class)
 public ResponseEntity<?> integridad(DataIntegrityViolationException ex){return ResponseEntity.status(409).body(Map.of("message","El registro ya existe o sus datos no cumplen las restricciones. Revise usuario, código de barras y registros vinculados."));}
}
