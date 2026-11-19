package mx.buap.veterinaria.expediente;
import java.time.LocalDateTime;
/** Datos de un adjunto sin su contenido binario. */
public record DocumentoResumen(Integer id, String nombre, String tipoMime, int tamanoBytes, String sha256,
        LocalDateTime creadoEn, String subidoPor, Integer estudioId) {}
