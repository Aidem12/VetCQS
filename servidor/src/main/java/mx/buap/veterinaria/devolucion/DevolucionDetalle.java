package mx.buap.veterinaria.devolucion;

import java.math.BigDecimal;
import jakarta.persistence.*;
import mx.buap.veterinaria.model.Producto;

@Entity @Table(name = "devolucion_detalle")
public class DevolucionDetalle {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "devolucion_id") private Devolucion devolucion;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "producto_id") private Producto producto;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal cantidad;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal importe;
    @Column(nullable = false) private boolean reingresa;

    protected DevolucionDetalle() {}
    public DevolucionDetalle(Producto producto, BigDecimal cantidad, BigDecimal importe, boolean reingresa) {
        this.producto = producto; this.cantidad = cantidad; this.importe = importe; this.reingresa = reingresa;
    }
    public Integer getId() { return id; }
    void setDevolucion(Devolucion d) { devolucion = d; }
    public Producto getProducto() { return producto; }
    public BigDecimal getCantidad() { return cantidad; }
    public BigDecimal getImporte() { return importe; }
    public boolean isReingresa() { return reingresa; }
}
