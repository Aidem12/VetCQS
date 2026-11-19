package mx.buap.veterinaria.expediente;

import java.time.*;
import jakarta.persistence.*;
import mx.buap.veterinaria.model.*;

@Entity @Table(name = "vacuna")
public class Vacuna implements Anulable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "mascota_id") private Mascota mascota;
    @Column(nullable = false, length = 120) private String vacuna;
    @Column(length = 40) private String lote;
    @Column(length = 40) private String dosis;
    @Column(name = "fecha_aplicacion", nullable = false) private LocalDate fechaAplicacion;
    @Column(name = "proximo_refuerzo") private LocalDate proximoRefuerzo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "veterinario_id") private Usuario veterinario;
    @Column(length = 500) private String observaciones;
    @Column(name = "creado_en", nullable = false) private LocalDateTime creadoEn = LocalDateTime.now();
    @Column(nullable = false) private boolean anulada;
    @Column(name = "motivo_anulacion", length = 200) private String motivoAnulacion;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "anulada_por") private Usuario anuladaPor;

    public Integer getId() { return id; }
    public Mascota getMascota() { return mascota; } public void setMascota(Mascota v) { mascota = v; }
    public String getVacuna() { return vacuna; } public void setVacuna(String v) { vacuna = v; }
    public String getLote() { return lote; } public void setLote(String v) { lote = v; }
    public String getDosis() { return dosis; } public void setDosis(String v) { dosis = v; }
    public LocalDate getFechaAplicacion() { return fechaAplicacion; } public void setFechaAplicacion(LocalDate v) { fechaAplicacion = v; }
    public LocalDate getProximoRefuerzo() { return proximoRefuerzo; } public void setProximoRefuerzo(LocalDate v) { proximoRefuerzo = v; }
    public Usuario getVeterinario() { return veterinario; } public void setVeterinario(Usuario v) { veterinario = v; }
    public String getObservaciones() { return observaciones; } public void setObservaciones(String v) { observaciones = v; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public boolean isAnulada() { return anulada; }
    public String getMotivoAnulacion() { return motivoAnulacion; }
    public Usuario getAnuladaPor() { return anuladaPor; }
    @Override public void anular(Usuario por, String motivo) { anulada = true; anuladaPor = por; motivoAnulacion = motivo; }
}
