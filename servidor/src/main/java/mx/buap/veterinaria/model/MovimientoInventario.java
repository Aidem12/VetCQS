package mx.buap.veterinaria.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity
@Table(name = "movimiento_inventario")
public class MovimientoInventario {
    public enum Tipo { ENTRADA, VENTA, AJUSTE, CANCELACION }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "producto_id")
    private Producto producto;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "usuario_id")
    private Usuario usuario;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12)
    private Tipo tipo;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal cantidad;
    @Column(nullable = false) private LocalDateTime fecha = LocalDateTime.now();
    @Column(length = 200) private String motivo;
    @Column(length = 80) private String referencia;
    public Integer getId() { return id; }
    public Producto getProducto() { return producto; } public void setProducto(Producto v) { producto=v; }
    public Usuario getUsuario() { return usuario; } public void setUsuario(Usuario v) { usuario=v; }
    public Tipo getTipo() { return tipo; } public void setTipo(Tipo v) { tipo=v; }
    public BigDecimal getCantidad() { return cantidad; } public void setCantidad(BigDecimal v) { cantidad=v; }
    public LocalDateTime getFecha() { return fecha; }
    public String getMotivo() { return motivo; } public void setMotivo(String v) { motivo=v; }
    public String getReferencia() { return referencia; } public void setReferencia(String v) { referencia=v; }
}
