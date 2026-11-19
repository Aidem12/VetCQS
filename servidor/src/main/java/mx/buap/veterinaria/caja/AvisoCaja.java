package mx.buap.veterinaria.caja;

import java.time.LocalDateTime;
import jakarta.persistence.*;

/** Aviso persistido para Caja: el WebSocket solo notifica, la caja recupera lo perdido por la API. */
@Entity @Table(name = "aviso_caja")
public class AvisoCaja {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @Column(nullable = false, length = 30) private String tipo;
    @Column(name = "venta_id") private Integer ventaId;
    @Column(nullable = false, length = 300) private String mensaje;
    @Column(name = "creado_en", nullable = false) private LocalDateTime creadoEn;

    protected AvisoCaja() {}
    public AvisoCaja(String tipo, Integer ventaId, String mensaje, LocalDateTime creadoEn) {
        this.tipo = tipo; this.ventaId = ventaId;
        this.mensaje = mensaje.length() > 300 ? mensaje.substring(0, 300) : mensaje; this.creadoEn = creadoEn;
    }
    public Integer getId() { return id; }
    public String getTipo() { return tipo; }
    public Integer getVentaId() { return ventaId; }
    public String getMensaje() { return mensaje; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
}
