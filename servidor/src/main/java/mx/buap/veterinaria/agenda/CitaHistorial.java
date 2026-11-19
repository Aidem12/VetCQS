package mx.buap.veterinaria.agenda;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import mx.buap.veterinaria.model.Usuario;

/** Registro inmutable de quién creó o cambió una cita. */
@Entity
@Table(name = "cita_historial")
public class CitaHistorial {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cita_id") private Cita cita;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "usuario_id") private Usuario usuario;
    @Column(nullable = false, length = 20) private String accion;
    @Column(length = 400) private String detalle;
    @Column(nullable = false) private LocalDateTime fecha = LocalDateTime.now();

    public CitaHistorial() {}
    public CitaHistorial(Cita cita, Usuario usuario, String accion, String detalle) {
        this.cita = cita; this.usuario = usuario; this.accion = accion;
        this.detalle = detalle == null || detalle.length() <= 400 ? detalle : detalle.substring(0, 400);
    }
    public Integer getId() { return id; }
    public Cita getCita() { return cita; }
    public Usuario getUsuario() { return usuario; }
    public String getAccion() { return accion; }
    public String getDetalle() { return detalle; }
    public LocalDateTime getFecha() { return fecha; }
}
