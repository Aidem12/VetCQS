package mx.buap.veterinaria.farmacia;

import jakarta.persistence.*;

@Entity @Table(name = "proveedor")
public class Proveedor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @Column(nullable = false, length = 150, unique = true) private String nombre;
    @Column(length = 13) private String rfc;
    @Column(length = 120) private String contacto;
    @Column(length = 20) private String telefono;
    @Column(length = 120) private String correo;
    @Column(length = 200) private String direccion;
    @Column(length = 500) private String notas;
    @Column(nullable = false) private boolean activo = true;

    public Integer getId() { return id; }
    public String getNombre() { return nombre; } public void setNombre(String v) { nombre = v; }
    public String getRfc() { return rfc; } public void setRfc(String v) { rfc = v; }
    public String getContacto() { return contacto; } public void setContacto(String v) { contacto = v; }
    public String getTelefono() { return telefono; } public void setTelefono(String v) { telefono = v; }
    public String getCorreo() { return correo; } public void setCorreo(String v) { correo = v; }
    public String getDireccion() { return direccion; } public void setDireccion(String v) { direccion = v; }
    public String getNotas() { return notas; } public void setNotas(String v) { notas = v; }
    public boolean isActivo() { return activo; } public void setActivo(boolean v) { activo = v; }
}
