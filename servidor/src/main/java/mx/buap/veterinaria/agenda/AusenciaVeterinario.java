package mx.buap.veterinaria.agenda;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import mx.buap.veterinaria.model.Usuario;

/** Vacaciones, ausencias o descansos extraordinarios que bloquean la agenda. */
@Entity
@Table(name = "ausencia_veterinario")
public class AusenciaVeterinario {
    public enum Tipo { VACACIONES, AUSENCIA, DESCANSO }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "veterinario_id") private Usuario veterinario;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) private Tipo tipo;
    @Column(nullable = false) private LocalDateTime inicio;
    @Column(nullable = false) private LocalDateTime fin;
    @Column(length = 200) private String motivo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "creado_por") private Usuario creadoPor;
    @Column(name = "creado_en", nullable = false) private LocalDateTime creadoEn = LocalDateTime.now();

    public Integer getId() { return id; }
    public Usuario getVeterinario() { return veterinario; }
    public void setVeterinario(Usuario veterinario) { this.veterinario = veterinario; }
    public Tipo getTipo() { return tipo; }
    public void setTipo(Tipo tipo) { this.tipo = tipo; }
    public LocalDateTime getInicio() { return inicio; }
    public void setInicio(LocalDateTime inicio) { this.inicio = inicio; }
    public LocalDateTime getFin() { return fin; }
    public void setFin(LocalDateTime fin) { this.fin = fin; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public Usuario getCreadoPor() { return creadoPor; }
    public void setCreadoPor(Usuario creadoPor) { this.creadoPor = creadoPor; }
}
