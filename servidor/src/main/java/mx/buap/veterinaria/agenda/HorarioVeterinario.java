package mx.buap.veterinaria.agenda;

import java.time.LocalTime;
import jakarta.persistence.*;
import mx.buap.veterinaria.model.Usuario;

/** Bloque de atención semanal de un veterinario (1 = lunes ... 7 = domingo). */
@Entity
@Table(name = "horario_veterinario")
public class HorarioVeterinario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "veterinario_id") private Usuario veterinario;
    @Column(name = "dia_semana", nullable = false) private short diaSemana;
    @Column(name = "hora_inicio", nullable = false) private LocalTime horaInicio;
    @Column(name = "hora_fin", nullable = false) private LocalTime horaFin;

    public Integer getId() { return id; }
    public Usuario getVeterinario() { return veterinario; }
    public void setVeterinario(Usuario veterinario) { this.veterinario = veterinario; }
    public short getDiaSemana() { return diaSemana; }
    public void setDiaSemana(short diaSemana) { this.diaSemana = diaSemana; }
    public LocalTime getHoraInicio() { return horaInicio; }
    public void setHoraInicio(LocalTime horaInicio) { this.horaInicio = horaInicio; }
    public LocalTime getHoraFin() { return horaFin; }
    public void setHoraFin(LocalTime horaFin) { this.horaFin = horaFin; }
}
