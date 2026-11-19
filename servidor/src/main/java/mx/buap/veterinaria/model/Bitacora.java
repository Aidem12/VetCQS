package mx.buap.veterinaria.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;
/** Bitácora de auditoría: cambios, autorizaciones y accesos. Nunca guarda contraseñas ni datos de tarjetas. */
@Entity @Table(name="bitacora")
public class Bitacora {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
 /** Puede ser nulo en intentos de acceso de un usuario inexistente o inactivo. */
 @Column(name="usuario_id") private Integer usuarioId;
 @Column(name="autorizo_id") private Integer autorizoId;
 @Column(nullable=false,length=40) private String accion;
 @Column(nullable=false,length=40) private String tabla;
 @Column(name="registro_id",nullable=false) private Integer registroId;
 @Column(columnDefinition="TEXT") private String detalle;
 @Column(nullable=false) private LocalDateTime fecha=LocalDateTime.now();
 @Column(name="usuario_texto",length=40) private String usuarioTexto;
 @Column(length=45) private String ip;
 public Bitacora() {}
 public Bitacora(Integer usuario,Integer autorizo,String accion,String tabla,Integer registro,String detalle) {
  this.usuarioId=usuario;this.autorizoId=autorizo;this.accion=accion;this.tabla=tabla;this.registroId=registro;this.detalle=detalle;
 }
 public Integer getId(){return id;}
 public Integer getUsuarioId(){return usuarioId;}
 public String getTabla(){return tabla;}
 public Integer getRegistroId(){return registroId;}
 public LocalDateTime getFecha(){return fecha;}
 public void setFecha(LocalDateTime fecha){this.fecha=fecha;}
 public String getUsuarioTexto(){return usuarioTexto;}
 public void setUsuarioTexto(String usuarioTexto){this.usuarioTexto=usuarioTexto;}
 public String getIp(){return ip;}
 public void setIp(String ip){this.ip=ip;}
 public String getDetalle(){return detalle;}
 public String getAccion(){return accion;}
 public Integer getAutorizoId(){return autorizoId;}
}
