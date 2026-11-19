package mx.buap.veterinaria.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "duenio")
public class Dueno {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(nullable = false, length = 120)
    private String nombre;
    @Column(length = 20)
    private String telefono;
    @Column(length = 120)
    private String correo;
    @Column(length = 200)
    private String direccion;
    @Column(name = "telefono_alterno", length = 20)
    private String telefonoAlterno;
    @Column(length = 500)
    private String notas;

    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public String getTelefonoAlterno() { return telefonoAlterno; }
    public void setTelefonoAlterno(String telefonoAlterno) { this.telefonoAlterno = telefonoAlterno; }
    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
}
