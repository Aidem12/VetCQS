package mx.buap.veterinaria.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

/** Primera entidad mapeada; sirve de modelo para las demás tablas en la iteración 1. */
@Entity
@Table(name = "producto")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "codigo_barras", length = 40)
    private String codigoBarras;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, length = 10)
    private String tipo; // PRODUCTO o SERVICIO

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    @Column(name = "stock_actual", nullable = false, precision = 10, scale = 2)
    private BigDecimal stockActual;

    @Column(name = "stock_minimo", nullable = false, precision = 10, scale = 2)
    private BigDecimal stockMinimo;

    @Column(name = "maneja_caducidad", nullable = false)
    private boolean manejaCaducidad;

    @Column(nullable = false)
    private boolean activo = true;

    public Integer getId() { return id; }
    public String getCodigoBarras() { return codigoBarras; }
    public void setCodigoBarras(String codigoBarras) { this.codigoBarras = codigoBarras; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }
    public BigDecimal getStockActual() { return stockActual; }
    public void setStockActual(BigDecimal stockActual) { this.stockActual = stockActual; }
    public BigDecimal getStockMinimo() { return stockMinimo; }
    public void setStockMinimo(BigDecimal stockMinimo) { this.stockMinimo = stockMinimo; }
    public boolean isManejaCaducidad() { return manejaCaducidad; }
    public void setManejaCaducidad(boolean manejaCaducidad) { this.manejaCaducidad = manejaCaducidad; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    @PrePersist
    @PreUpdate
    private void normalizar() {
        if (tipo != null) {
            tipo = tipo.trim().toUpperCase();
        }
        if (codigoBarras != null && codigoBarras.isBlank()) {
            codigoBarras = null;
        }
    }
}
