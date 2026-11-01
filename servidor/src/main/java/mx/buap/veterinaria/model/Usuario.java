package mx.buap.veterinaria.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "usuario")
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(nullable = false, length = 100)
    private String nombre;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Rol rol;
    @Column(nullable = false)
    private boolean activo;
    @Column(nullable = false, unique = true, length = 40)
    private String usuario;
    @Column(name = "contrasena_hash", nullable = false, length = 100)
    @JsonIgnore
    private String contrasenaHash;
    @Column(name = "creado_en", nullable = false) private LocalDateTime creadoEn;
    @Column(name = "actualizado_en", nullable = false) private LocalDateTime actualizadoEn;
    public enum Rol { CAJERO, VETERINARIO, ADMINISTRADOR }
    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
    public Rol getRol() { return rol; }
    public boolean isActivo() { return activo; }
    public String getUsuario() { return usuario; } public void setUsuario(String v) { usuario=v; }
    public String getContrasenaHash() { return contrasenaHash; } public void setContrasenaHash(String v) { contrasenaHash=v; }
    public void setNombre(String v) { nombre=v; } public void setRol(Rol v) { rol=v; } public void setActivo(boolean v) { activo=v; }
    @PrePersist void crearFechas() { if (creadoEn == null) creadoEn=LocalDateTime.now(); if (actualizadoEn == null) actualizadoEn=creadoEn; }
    @PreUpdate void actualizarFecha() { actualizadoEn=LocalDateTime.now(); }
}
