package mx.buap.veterinaria.agenda;

import jakarta.persistence.*;
import mx.buap.veterinaria.model.Producto;

/** Tipo de consulta o servicio con su duración configurable. */
@Entity
@Table(name = "tipo_cita")
public class TipoCita {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @Column(nullable = false, length = 80, unique = true) private String nombre;
    @Column(name = "duracion_min", nullable = false) private int duracionMin;
    @Column(nullable = false, length = 7) private String color = "#2F7DC1";
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "producto_id") private Producto producto;
    @Column(nullable = false) private boolean activo = true;

    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public int getDuracionMin() { return duracionMin; }
    public void setDuracionMin(int duracionMin) { this.duracionMin = duracionMin; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public Producto getProducto() { return producto; }
    public void setProducto(Producto producto) { this.producto = producto; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
