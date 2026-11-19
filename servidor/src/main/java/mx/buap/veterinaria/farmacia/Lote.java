package mx.buap.veterinaria.farmacia;

import java.math.BigDecimal;
import java.time.*;
import jakarta.persistence.*;
import mx.buap.veterinaria.model.Producto;

/** Lote de un producto con caducidad; "cantidad" es lo disponible. */
@Entity @Table(name = "lote")
public class Lote {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "producto_id") private Producto producto;
    @Column(name = "numero_lote", nullable = false, length = 40) private String numeroLote;
    @Column(nullable = false) private LocalDate caducidad;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal cantidad;
    @Column(name = "cantidad_recibida", precision = 10, scale = 2) private BigDecimal cantidadRecibida;
    @Column(name = "costo_unitario", precision = 10, scale = 2) private BigDecimal costoUnitario;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "proveedor_id") private Proveedor proveedor;
    @Column(name = "compra_id") private Integer compraId;
    @Column(name = "fecha_recepcion", nullable = false) private LocalDateTime fechaRecepcion = LocalDateTime.now();

    public Integer getId() { return id; }
    public Producto getProducto() { return producto; } public void setProducto(Producto v) { producto = v; }
    public String getNumeroLote() { return numeroLote; } public void setNumeroLote(String v) { numeroLote = v; }
    public LocalDate getCaducidad() { return caducidad; } public void setCaducidad(LocalDate v) { caducidad = v; }
    public BigDecimal getCantidad() { return cantidad; } public void setCantidad(BigDecimal v) { cantidad = v; }
    public BigDecimal getCantidadRecibida() { return cantidadRecibida; } public void setCantidadRecibida(BigDecimal v) { cantidadRecibida = v; }
    public BigDecimal getCostoUnitario() { return costoUnitario; } public void setCostoUnitario(BigDecimal v) { costoUnitario = v; }
    public Proveedor getProveedor() { return proveedor; } public void setProveedor(Proveedor v) { proveedor = v; }
    public Integer getCompraId() { return compraId; } public void setCompraId(Integer v) { compraId = v; }
    public LocalDateTime getFechaRecepcion() { return fechaRecepcion; }
    public boolean vencido(LocalDate hoy) { return caducidad.isBefore(hoy); }
}
