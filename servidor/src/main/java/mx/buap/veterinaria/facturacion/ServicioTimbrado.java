package mx.buap.veterinaria.facturacion;

import mx.buap.veterinaria.model.Factura;

/**
 * Punto de extensión para timbrar CFDI con un PAC autorizado. La única implementación incluida es
 * {@link TimbradoDemostracion}, que NO se comunica con ningún PAC ni con el SAT y marca la factura como simulada.
 * Para producción se debe agregar otra implementación (con credenciales del PAC en variables de entorno)
 * y activarla con app.facturacion.proveedor.
 */
public interface ServicioTimbrado {
    /** Resultado del intento: estado (TIMBRADA/ERROR), UUID fiscal, XML y respuesta del proveedor para trazabilidad. */
    record Resultado(String estado, java.util.UUID uuid, String xml, String respuesta, boolean simulado) {}

    Resultado timbrar(Factura factura);

    String nombre();
}
