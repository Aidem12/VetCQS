package mx.buap.veterinaria.expediente;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import mx.buap.veterinaria.model.*;

/** Archivo adjunto (PDF, PNG o JPEG, máximo 5 MB) guardado en la base con su huella SHA-256. */
@Entity @Table(name = "documento_clinico")
public class DocumentoClinico {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "mascota_id") private Mascota mascota;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "estudio_id") private Estudio estudio;
    @Column(name = "nombre_original", nullable = false, length = 200) private String nombreOriginal;
    @Column(name = "tipo_mime", nullable = false, length = 60) private String tipoMime;
    @Column(name = "tamano_bytes", nullable = false) private int tamanoBytes;
    @Column(nullable = false, length = 64) private String sha256;
    @Basic(fetch = FetchType.LAZY) @Column(nullable = false, columnDefinition = "bytea") private byte[] contenido;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "subido_por") private Usuario subidoPor;
    @Column(name = "creado_en", nullable = false) private LocalDateTime creadoEn = LocalDateTime.now();

    public Integer getId() { return id; }
    public Mascota getMascota() { return mascota; } public void setMascota(Mascota v) { mascota = v; }
    public Estudio getEstudio() { return estudio; } public void setEstudio(Estudio v) { estudio = v; }
    public String getNombreOriginal() { return nombreOriginal; } public void setNombreOriginal(String v) { nombreOriginal = v; }
    public String getTipoMime() { return tipoMime; } public void setTipoMime(String v) { tipoMime = v; }
    public int getTamanoBytes() { return tamanoBytes; } public void setTamanoBytes(int v) { tamanoBytes = v; }
    public String getSha256() { return sha256; } public void setSha256(String v) { sha256 = v; }
    public byte[] getContenido() { return contenido; } public void setContenido(byte[] v) { contenido = v; }
    public Usuario getSubidoPor() { return subidoPor; } public void setSubidoPor(Usuario v) { subidoPor = v; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
}
