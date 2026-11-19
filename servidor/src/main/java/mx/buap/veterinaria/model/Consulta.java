package mx.buap.veterinaria.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "consulta")
public class Consulta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mascota_id", nullable = false)
    private Mascota mascota;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "veterinario_id", nullable = false)
    private Usuario veterinario;
    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();
    @Column(nullable = false, length = 250)
    private String motivo;
    @Column(name = "peso_kg", precision = 6, scale = 2)
    private BigDecimal pesoKg;
    @Column(name = "temperatura_c", precision = 4, scale = 1)
    private BigDecimal temperaturaC;
    @Column(columnDefinition = "TEXT")
    private String diagnostico;
    @Column(columnDefinition = "TEXT")
    private String tratamiento;
    @Column(name = "frecuencia_cardiaca")
    private Integer frecuenciaCardiaca;
    @Column(name = "frecuencia_respiratoria")
    private Integer frecuenciaRespiratoria;
    @Column(name = "condicion_corporal")
    private Short condicionCorporal;
    @Column(columnDefinition = "TEXT")
    private String evolucion;

    public Integer getId() { return id; }
    public Mascota getMascota() { return mascota; }
    public void setMascota(Mascota mascota) { this.mascota = mascota; }
    public Usuario getVeterinario() { return veterinario; }
    public void setVeterinario(Usuario veterinario) { this.veterinario = veterinario; }
    public LocalDateTime getFecha() { return fecha; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public BigDecimal getPesoKg() { return pesoKg; }
    public void setPesoKg(BigDecimal pesoKg) { this.pesoKg = pesoKg; }
    public BigDecimal getTemperaturaC() { return temperaturaC; }
    public void setTemperaturaC(BigDecimal temperaturaC) { this.temperaturaC = temperaturaC; }
    public String getDiagnostico() { return diagnostico; }
    public void setDiagnostico(String diagnostico) { this.diagnostico = diagnostico; }
    public String getTratamiento() { return tratamiento; }
    public void setTratamiento(String tratamiento) { this.tratamiento = tratamiento; }
    public Integer getFrecuenciaCardiaca() { return frecuenciaCardiaca; }
    public void setFrecuenciaCardiaca(Integer v) { this.frecuenciaCardiaca = v; }
    public Integer getFrecuenciaRespiratoria() { return frecuenciaRespiratoria; }
    public void setFrecuenciaRespiratoria(Integer v) { this.frecuenciaRespiratoria = v; }
    public Short getCondicionCorporal() { return condicionCorporal; }
    public void setCondicionCorporal(Short v) { this.condicionCorporal = v; }
    public String getEvolucion() { return evolucion; }
    public void setEvolucion(String evolucion) { this.evolucion = evolucion; }
}
