package mx.buap.veterinaria.expediente;

import jakarta.persistence.*;
import mx.buap.veterinaria.model.Producto;

@Entity @Table(name = "receta_detalle")
public class RecetaDetalle {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "receta_id") private Receta receta;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "producto_id") private Producto producto;
    @Column(nullable = false, length = 160) private String medicamento;
    @Column(nullable = false, length = 80) private String dosis;
    @Column(nullable = false, length = 80) private String frecuencia;
    @Column(nullable = false, length = 40) private String via;
    @Column(nullable = false, length = 60) private String duracion;
    @Column(length = 400) private String indicaciones;

    public Integer getId() { return id; }
    public Receta getReceta() { return receta; } void setReceta(Receta v) { receta = v; }
    public Producto getProducto() { return producto; } public void setProducto(Producto v) { producto = v; }
    public String getMedicamento() { return medicamento; } public void setMedicamento(String v) { medicamento = v; }
    public String getDosis() { return dosis; } public void setDosis(String v) { dosis = v; }
    public String getFrecuencia() { return frecuencia; } public void setFrecuencia(String v) { frecuencia = v; }
    public String getVia() { return via; } public void setVia(String v) { via = v; }
    public String getDuracion() { return duracion; } public void setDuracion(String v) { duracion = v; }
    public String getIndicaciones() { return indicaciones; } public void setIndicaciones(String v) { indicaciones = v; }
}
