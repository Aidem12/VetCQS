package mx.buap.veterinaria.model;
import java.time.LocalDateTime;
import jakarta.persistence.*;
@Entity @Table(name="factura")
public class Factura {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
 @OneToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="venta_id", unique=true) private Venta venta;
 @Column(name="rfc_receptor",nullable=false,length=13) private String rfcReceptor;
 @Column(name="razon_social",nullable=false,length=250) private String razonSocial;
 @Column(name="regimen_fiscal",nullable=false,length=3) private String regimenFiscal;
 @Column(name="codigo_postal",nullable=false,length=5) private String codigoPostal;
 @Column(name="uso_cfdi",nullable=false,length=4) private String usoCfdi;
 @Column(nullable=false,length=10) private String estado="PENDIENTE";
 @Column(nullable=false) private int intentos;
 @Column(name="uuid_sat") private java.util.UUID uuidSat;
 @Column(name="xml") private String xml;
 @Column(name="fecha_solicitud",nullable=false) private LocalDateTime fechaSolicitud=LocalDateTime.now();
 @Column(name="fecha_timbrado") private LocalDateTime fechaTimbrado;
 @Column(nullable=false) private boolean mock=true;
 @Column(length=80) private String idempotencia;
 @Column(name="respuesta_mock") private String respuestaMock;
 public Integer getId(){return id;} public Venta getVenta(){return venta;} public void setVenta(Venta v){venta=v;}
 public String getRfcReceptor(){return rfcReceptor;} public void setRfcReceptor(String v){rfcReceptor=v;}
 public String getRazonSocial(){return razonSocial;} public void setRazonSocial(String v){razonSocial=v;}
 public String getRegimenFiscal(){return regimenFiscal;} public void setRegimenFiscal(String v){regimenFiscal=v;}
 public String getCodigoPostal(){return codigoPostal;} public void setCodigoPostal(String v){codigoPostal=v;}
 public String getUsoCfdi(){return usoCfdi;} public void setUsoCfdi(String v){usoCfdi=v;}
 public String getEstado(){return estado;} public void setEstado(String v){estado=v;}
 public int getIntentos(){return intentos;} public void setIntentos(int v){intentos=v;}
 public java.util.UUID getUuidSat(){return uuidSat;} public void setUuidSat(java.util.UUID v){uuidSat=v;}
 public String getXml(){return xml;} public void setXml(String v){xml=v;}
 public LocalDateTime getFechaSolicitud(){return fechaSolicitud;}
 public boolean isMock(){return mock;} public void setMock(boolean v){mock=v;}
 public String getIdempotencia(){return idempotencia;} public void setIdempotencia(String v){idempotencia=v;}
 public String getRespuestaMock(){return respuestaMock;} public void setRespuestaMock(String v){respuestaMock=v;}
}
