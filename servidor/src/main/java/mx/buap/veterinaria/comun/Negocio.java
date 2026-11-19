package mx.buap.veterinaria.comun;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Datos comerciales configurables por variables de entorno (tickets, recetas y reportes). */
@Component
public class Negocio {
    private final String nombre, direccion, telefono, rfc, leyenda;
    public Negocio(@Value("${app.negocio.nombre:Clínica Veterinaria}") String nombre,
            @Value("${app.negocio.direccion:}") String direccion, @Value("${app.negocio.telefono:}") String telefono,
            @Value("${app.negocio.rfc:}") String rfc, @Value("${app.negocio.leyenda:}") String leyenda) {
        this.nombre = nombre; this.direccion = direccion; this.telefono = telefono; this.rfc = rfc; this.leyenda = leyenda;
    }
    public String getNombre() { return nombre; }
    public String getDireccion() { return direccion; }
    public String getTelefono() { return telefono; }
    public String getRfc() { return rfc; }
    public String getLeyenda() { return leyenda; }
    /** Línea de encabezado para documentos impresos. */
    public String encabezado() {
        StringBuilder s = new StringBuilder(nombre);
        if (!direccion.isBlank()) s.append(" · ").append(direccion);
        if (!telefono.isBlank()) s.append(" · Tel. ").append(telefono);
        return s.toString();
    }
}
