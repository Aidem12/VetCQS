package mx.buap.veterinaria.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="bitacora")
public class Bitacora {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
 @Column(name="usuario_id",nullable=false) private Integer usuarioId;
 @Column(name="autorizo_id") private Integer autorizoId;
 @Column(nullable=false,length=40) private String accion;
 @Column(nullable=false,length=40) private String tabla;
 @Column(name="registro_id",nullable=false) private Integer registroId;
 @Column(columnDefinition="TEXT") private String detalle;
 @Column(nullable=false) private LocalDateTime fecha=LocalDateTime.now();
 public Bitacora() {}
 public Bitacora(Integer usuario,Integer autorizo,String accion,String tabla,Integer registro,String detalle) {
  this.usuarioId=usuario;this.autorizoId=autorizo;this.accion=accion;this.tabla=tabla;this.registroId=registro;this.detalle=detalle;
 }
 public String getDetalle(){return detalle;}
 public String getAccion(){return accion;}
 public Integer getAutorizoId(){return autorizoId;}
}
