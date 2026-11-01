package mx.buap.veterinaria.model;

import jakarta.persistence.*;

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
    public enum Rol { CAJERO, VETERINARIO, ADMINISTRADOR }
    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
    public Rol getRol() { return rol; }
    public boolean isActivo() { return activo; }
}
