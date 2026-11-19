package mx.buap.veterinaria.farmacia;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import jakarta.persistence.*;
import mx.buap.veterinaria.model.Usuario;

@Entity @Table(name = "compra")
public class Compra {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "proveedor_id") private Proveedor proveedor;
    @Column(length = 60) private String documento;
    @Column(nullable = false) private LocalDateTime fecha = LocalDateTime.now();
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "usuario_id") private Usuario usuario;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal total = BigDecimal.ZERO;
    @Column(length = 300) private String notas;
    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL) @OrderBy("id") private List<CompraDetalle> detalles = new ArrayList<>();

    public Integer getId() { return id; }
    public Proveedor getProveedor() { return proveedor; } public void setProveedor(Proveedor v) { proveedor = v; }
    public String getDocumento() { return documento; } public void setDocumento(String v) { documento = v; }
    public LocalDateTime getFecha() { return fecha; }
    public Usuario getUsuario() { return usuario; } public void setUsuario(Usuario v) { usuario = v; }
    public BigDecimal getTotal() { return total; } public void setTotal(BigDecimal v) { total = v; }
    public String getNotas() { return notas; } public void setNotas(String v) { notas = v; }
    public List<CompraDetalle> getDetalles() { return detalles; }
    public void agregar(CompraDetalle d) { d.setCompra(this); detalles.add(d); }
}
