package mx.buap.veterinaria.expediente;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import mx.buap.veterinaria.model.*;

/** Alergias, enfermedades preexistentes, cirugías, antecedentes y alertas clínicas. */
@Entity @Table(name = "antecedente_clinico")
public class AntecedenteClinico {
    public enum Tipo { ALERGIA, ENFERMEDAD, CIRUGIA, ANTECEDENTE, ALERTA }
    public enum Severidad { LEVE, MODERADA, GRAVE }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "mascota_id") private Mascota mascota;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Tipo tipo;
    @Column(nullable = false, length = 300) private String descripcion;
    @Enumerated(EnumType.STRING) @Column(length = 10) private Severidad severidad;
    @Column(nullable = false) private boolean vigente = true;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "registrado_por") private Usuario registradoPor;
    @Column(name = "creado_en", nullable = false) private LocalDateTime creadoEn = LocalDateTime.now();
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "cerrado_por") private Usuario cerradoPor;
    @Column(name = "cerrado_en") private LocalDateTime cerradoEn;

    public Integer getId() { return id; }
    public Mascota getMascota() { return mascota; } public void setMascota(Mascota v) { mascota = v; }
    public Tipo getTipo() { return tipo; } public void setTipo(Tipo v) { tipo = v; }
    public String getDescripcion() { return descripcion; } public void setDescripcion(String v) { descripcion = v; }
    public Severidad getSeveridad() { return severidad; } public void setSeveridad(Severidad v) { severidad = v; }
    public boolean isVigente() { return vigente; }
    public Usuario getRegistradoPor() { return registradoPor; } public void setRegistradoPor(Usuario v) { registradoPor = v; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void cerrar(Usuario por, LocalDateTime cuando) { vigente = false; cerradoPor = por; cerradoEn = cuando; }
}
