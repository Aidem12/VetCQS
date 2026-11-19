package mx.buap.veterinaria.agenda;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import mx.buap.veterinaria.model.*;

/** Cita de una mascota con un veterinario. Los traslapes los impide también PostgreSQL. */
@Entity
@Table(name = "cita")
public class Cita {
    public enum Estado { PENDIENTE, CONFIRMADA, EN_CURSO, ATENDIDA, CANCELADA, NO_ASISTIO;
        public boolean activa() { return this != CANCELADA && this != NO_ASISTIO; }
        public boolean cerrada() { return this == ATENDIDA || this == CANCELADA || this == NO_ASISTIO; }
    }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "mascota_id") private Mascota mascota;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "veterinario_id") private Usuario veterinario;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "tipo_cita_id") private TipoCita tipo;
    @Column(nullable = false) private LocalDateTime inicio;
    @Column(nullable = false) private LocalDateTime fin;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) private Estado estado = Estado.PENDIENTE;
    @Column(length = 250) private String motivo;
    @Column(columnDefinition = "TEXT") private String notas;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "consulta_id") private Consulta consulta;
    @Column(name = "motivo_cancelacion", length = 200) private String motivoCancelacion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "creado_por") private Usuario creadoPor;
    @Column(name = "creado_en", nullable = false) private LocalDateTime creadoEn = LocalDateTime.now();
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "actualizado_por") private Usuario actualizadoPor;
    @Column(name = "actualizado_en") private LocalDateTime actualizadoEn;

    public Integer getId() { return id; }
    public Mascota getMascota() { return mascota; }
    public void setMascota(Mascota mascota) { this.mascota = mascota; }
    public Usuario getVeterinario() { return veterinario; }
    public void setVeterinario(Usuario veterinario) { this.veterinario = veterinario; }
    public TipoCita getTipo() { return tipo; }
    public void setTipo(TipoCita tipo) { this.tipo = tipo; }
    public LocalDateTime getInicio() { return inicio; }
    public void setInicio(LocalDateTime inicio) { this.inicio = inicio; }
    public LocalDateTime getFin() { return fin; }
    public void setFin(LocalDateTime fin) { this.fin = fin; }
    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
    public Consulta getConsulta() { return consulta; }
    public void setConsulta(Consulta consulta) { this.consulta = consulta; }
    public String getMotivoCancelacion() { return motivoCancelacion; }
    public void setMotivoCancelacion(String motivoCancelacion) { this.motivoCancelacion = motivoCancelacion; }
    public Usuario getCreadoPor() { return creadoPor; }
    public void setCreadoPor(Usuario creadoPor) { this.creadoPor = creadoPor; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public Usuario getActualizadoPor() { return actualizadoPor; }
    public void setActualizadoPor(Usuario actualizadoPor) { this.actualizadoPor = actualizadoPor; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(LocalDateTime actualizadoEn) { this.actualizadoEn = actualizadoEn; }
}
