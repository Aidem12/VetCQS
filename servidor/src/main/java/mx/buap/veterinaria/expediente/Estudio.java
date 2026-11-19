package mx.buap.veterinaria.expediente;

import java.time.*;
import jakarta.persistence.*;
import mx.buap.veterinaria.model.*;

/** Estudio de laboratorio o gabinete con su resultado. */
@Entity @Table(name = "estudio")
public class Estudio {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "mascota_id") private Mascota mascota;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "consulta_id") private Consulta consulta;
    @Column(nullable = false, length = 80) private String tipo;
    @Column(length = 300) private String descripcion;
    @Column(columnDefinition = "TEXT") private String resultado;
    @Column(nullable = false) private LocalDate fecha;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "registrado_por") private Usuario registradoPor;
    @Column(name = "creado_en", nullable = false) private LocalDateTime creadoEn = LocalDateTime.now();

    public Integer getId() { return id; }
    public Mascota getMascota() { return mascota; } public void setMascota(Mascota v) { mascota = v; }
    public Consulta getConsulta() { return consulta; } public void setConsulta(Consulta v) { consulta = v; }
    public String getTipo() { return tipo; } public void setTipo(String v) { tipo = v; }
    public String getDescripcion() { return descripcion; } public void setDescripcion(String v) { descripcion = v; }
    public String getResultado() { return resultado; } public void setResultado(String v) { resultado = v; }
    public LocalDate getFecha() { return fecha; } public void setFecha(LocalDate v) { fecha = v; }
    public Usuario getRegistradoPor() { return registradoPor; } public void setRegistradoPor(Usuario v) { registradoPor = v; }
}
