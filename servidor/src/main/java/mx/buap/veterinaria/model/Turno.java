package mx.buap.veterinaria.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity
@Table(name = "turno")
public class Turno {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cajero_id")
    private Usuario cajero;
    @Column(nullable = false) private LocalDateTime apertura = LocalDateTime.now();
    @Column(name = "fondo_inicial", nullable = false, precision = 10, scale = 2) private BigDecimal fondoInicial;
    private LocalDateTime cierre;
    @Column(name = "efectivo_contado", precision = 10, scale = 2) private BigDecimal efectivoContado;
    @Column(name = "efectivo_esperado", precision = 10, scale = 2) private BigDecimal efectivoEsperado;

    public Integer getId() { return id; }
    public Usuario getCajero() { return cajero; } public void setCajero(Usuario v) { cajero = v; }
    public LocalDateTime getApertura() { return apertura; }
    public BigDecimal getFondoInicial() { return fondoInicial; } public void setFondoInicial(BigDecimal v) { fondoInicial = v; }
    public LocalDateTime getCierre() { return cierre; }
    public BigDecimal getEfectivoContado() { return efectivoContado; }
    public BigDecimal getEfectivoEsperado() { return efectivoEsperado; }
    public void cerrar(BigDecimal contado, BigDecimal esperado) {
        efectivoContado = contado; efectivoEsperado = esperado; cierre = LocalDateTime.now();
    }
}
