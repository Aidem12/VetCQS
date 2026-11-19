package mx.buap.veterinaria.expediente;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import mx.buap.veterinaria.model.*;

/** Nota de evolución o aclaración agregada a una consulta (las consultas no se editan). */
@Entity @Table(name = "nota_clinica")
public class NotaClinica {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "consulta_id") private Consulta consulta;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "autor_id") private Usuario autor;
    @Column(nullable = false, columnDefinition = "TEXT") private String texto;
    @Column(name = "creado_en", nullable = false) private LocalDateTime creadoEn = LocalDateTime.now();

    public Integer getId() { return id; }
    public Consulta getConsulta() { return consulta; } public void setConsulta(Consulta v) { consulta = v; }
    public Usuario getAutor() { return autor; } public void setAutor(Usuario v) { autor = v; }
    public String getTexto() { return texto; } public void setTexto(String v) { texto = v; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
}
