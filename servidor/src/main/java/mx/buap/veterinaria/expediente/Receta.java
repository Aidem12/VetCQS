package mx.buap.veterinaria.expediente;

import java.time.LocalDateTime;
import java.util.*;
import jakarta.persistence.*;
import mx.buap.veterinaria.model.*;

@Entity @Table(name = "receta")
public class Receta {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @Column(length = 20, unique = true) private String folio;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "mascota_id") private Mascota mascota;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "consulta_id") private Consulta consulta;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "veterinario_id") private Usuario veterinario;
    @Column(nullable = false) private LocalDateTime fecha = LocalDateTime.now();
    @Column(name = "indicaciones_generales", columnDefinition = "TEXT") private String indicacionesGenerales;
    @OneToMany(mappedBy = "receta", cascade = CascadeType.ALL, orphanRemoval = true) @OrderBy("id")
    private List<RecetaDetalle> detalles = new ArrayList<>();

    public Integer getId() { return id; }
    public String getFolio() { return folio; } public void setFolio(String v) { folio = v; }
    public Mascota getMascota() { return mascota; } public void setMascota(Mascota v) { mascota = v; }
    public Consulta getConsulta() { return consulta; } public void setConsulta(Consulta v) { consulta = v; }
    public Usuario getVeterinario() { return veterinario; } public void setVeterinario(Usuario v) { veterinario = v; }
    public LocalDateTime getFecha() { return fecha; }
    public String getIndicacionesGenerales() { return indicacionesGenerales; } public void setIndicacionesGenerales(String v) { indicacionesGenerales = v; }
    public List<RecetaDetalle> getDetalles() { return detalles; }
    public void agregar(RecetaDetalle d) { d.setReceta(this); detalles.add(d); }
}
