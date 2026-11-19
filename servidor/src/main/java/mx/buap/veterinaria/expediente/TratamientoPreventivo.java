package mx.buap.veterinaria.expediente;

import java.time.*;
import jakarta.persistence.*;
import mx.buap.veterinaria.model.*;

@Entity @Table(name = "tratamiento_preventivo")
public class TratamientoPreventivo implements Anulable {
    public enum Tipo { DESPARASITACION_INTERNA, DESPARASITACION_EXTERNA, OTRO }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "mascota_id") private Mascota mascota;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private Tipo tipo;
    @Column(nullable = false, length = 120) private String producto;
    @Column(length = 60) private String dosis;
    @Column(name = "fecha_aplicacion", nullable = false) private LocalDate fechaAplicacion;
    @Column(name = "proxima_aplicacion") private LocalDate proximaAplicacion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "veterinario_id") private Usuario veterinario;
    @Column(length = 500) private String observaciones;
    @Column(name = "creado_en", nullable = false) private LocalDateTime creadoEn = LocalDateTime.now();
    @Column(nullable = false) private boolean anulada;
    @Column(name = "motivo_anulacion", length = 200) private String motivoAnulacion;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "anulada_por") private Usuario anuladaPor;

    public Integer getId() { return id; }
    public Mascota getMascota() { return mascota; } public void setMascota(Mascota v) { mascota = v; }
    public Tipo getTipo() { return tipo; } public void setTipo(Tipo v) { tipo = v; }
    public String getProducto() { return producto; } public void setProducto(String v) { producto = v; }
    public String getDosis() { return dosis; } public void setDosis(String v) { dosis = v; }
    public LocalDate getFechaAplicacion() { return fechaAplicacion; } public void setFechaAplicacion(LocalDate v) { fechaAplicacion = v; }
    public LocalDate getProximaAplicacion() { return proximaAplicacion; } public void setProximaAplicacion(LocalDate v) { proximaAplicacion = v; }
    public Usuario getVeterinario() { return veterinario; } public void setVeterinario(Usuario v) { veterinario = v; }
    public String getObservaciones() { return observaciones; } public void setObservaciones(String v) { observaciones = v; }
    public boolean isAnulada() { return anulada; }
    public String getMotivoAnulacion() { return motivoAnulacion; }
    @Override public void anular(Usuario por, String motivo) { anulada = true; anuladaPor = por; motivoAnulacion = motivo; }
}
