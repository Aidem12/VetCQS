package mx.buap.veterinaria.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "pago")
public class Pago {

    public enum Forma { EFECTIVO, TARJETA, TRANSFERENCIA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @Column(nullable = false, length = 15)
    @Enumerated(EnumType.STRING)
    private Forma forma;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Column(precision = 10, scale = 2)
    private BigDecimal recibido;

    public Integer getId() { return id; }
    public Venta getVenta() { return venta; }
    public void setVenta(Venta venta) { this.venta = venta; }
    public Forma getForma() { return forma; }
    public void setForma(Forma forma) { this.forma = forma; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public BigDecimal getRecibido() { return recibido; }
    public void setRecibido(BigDecimal recibido) { this.recibido = recibido; }
}
