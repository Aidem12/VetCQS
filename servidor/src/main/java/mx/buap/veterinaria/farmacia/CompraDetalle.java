package mx.buap.veterinaria.farmacia;

import java.math.BigDecimal;
import jakarta.persistence.*;
import mx.buap.veterinaria.model.Producto;

@Entity @Table(name = "compra_detalle")
public class CompraDetalle {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "compra_id") private Compra compra;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "producto_id") private Producto producto;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "lote_id") private Lote lote;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal cantidad;
    @Column(name = "costo_unitario", nullable = false, precision = 10, scale = 2) private BigDecimal costoUnitario;

    public Integer getId() { return id; }
    public Compra getCompra() { return compra; } void setCompra(Compra v) { compra = v; }
    public Producto getProducto() { return producto; } public void setProducto(Producto v) { producto = v; }
    public Lote getLote() { return lote; } public void setLote(Lote v) { lote = v; }
    public BigDecimal getCantidad() { return cantidad; } public void setCantidad(BigDecimal v) { cantidad = v; }
    public BigDecimal getCostoUnitario() { return costoUnitario; } public void setCostoUnitario(BigDecimal v) { costoUnitario = v; }
}
