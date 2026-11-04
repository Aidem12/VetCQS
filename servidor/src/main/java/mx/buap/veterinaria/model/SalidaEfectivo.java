package mx.buap.veterinaria.model;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;
@Entity @Table(name="salida_efectivo")
public class SalidaEfectivo {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
 @Column(name="turno_id",nullable=false) private Integer turnoId;
 @Column(nullable=false,length=200) private String concepto;
 @Column(nullable=false,precision=10,scale=2) private BigDecimal monto;
 @Column(name="autorizado_por",nullable=false) private Integer autorizadoPor;
 @Column(nullable=false) private LocalDateTime fecha=LocalDateTime.now();
 public SalidaEfectivo() {}
 public SalidaEfectivo(Integer turno,String concepto,BigDecimal monto,Integer admin){this.turnoId=turno;this.concepto=concepto;this.monto=monto;this.autorizadoPor=admin;}
 public Integer getId(){return id;} public String getConcepto(){return concepto;}
 public BigDecimal getMonto(){return monto;} public LocalDateTime getFecha(){return fecha;}
}
