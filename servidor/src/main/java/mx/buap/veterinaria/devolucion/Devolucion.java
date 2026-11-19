package mx.buap.veterinaria.devolucion;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.*;

/** Devolución o reembolso de una venta pagada. Nunca modifica la venta original: la compensa. */
@Entity @Table(name = "devolucion")
public class Devolucion {
    public enum Forma { EFECTIVO, TARJETA, TRANSFERENCIA }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @Column(nullable = false, length = 20) private String folio;
    @Column(name = "venta_id", nullable = false) private Integer ventaId;
    @Column(name = "turno_id") private Integer turnoId;
    @Column(name = "usuario_id", nullable = false) private Integer usuarioId;
    @Column(name = "autorizado_por", nullable = false) private Integer autorizadoPor;
    @Column(nullable = false, length = 200) private String motivo;
    @Enumerated(EnumType.STRING) @Column(name = "forma_reembolso", nullable = false, length = 15) private Forma formaReembolso;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal monto;
    @Column(nullable = false) private LocalDateTime fecha;
    @OneToMany(mappedBy = "devolucion", cascade = CascadeType.ALL, orphanRemoval = true) private List<DevolucionDetalle> detalles = new ArrayList<>();

    public Integer getId() { return id; }
    public String getFolio() { return folio; } public void setFolio(String v) { folio = v; }
    public Integer getVentaId() { return ventaId; } public void setVentaId(Integer v) { ventaId = v; }
    public Integer getTurnoId() { return turnoId; } public void setTurnoId(Integer v) { turnoId = v; }
    public Integer getUsuarioId() { return usuarioId; } public void setUsuarioId(Integer v) { usuarioId = v; }
    public Integer getAutorizadoPor() { return autorizadoPor; } public void setAutorizadoPor(Integer v) { autorizadoPor = v; }
    public String getMotivo() { return motivo; } public void setMotivo(String v) { motivo = v; }
    public Forma getFormaReembolso() { return formaReembolso; } public void setFormaReembolso(Forma v) { formaReembolso = v; }
    public BigDecimal getMonto() { return monto; } public void setMonto(BigDecimal v) { monto = v; }
    public LocalDateTime getFecha() { return fecha; } public void setFecha(LocalDateTime v) { fecha = v; }
    public List<DevolucionDetalle> getDetalles() { return detalles; }
    public void agregar(DevolucionDetalle d) { d.setDevolucion(this); detalles.add(d); }
}
