package mx.buap.veterinaria.facturacion;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import mx.buap.veterinaria.model.Factura;

/** Timbrado de DEMOSTRACIÓN: genera un identificador determinista marcado como simulado. No tiene validez fiscal. */
@Component
@ConditionalOnProperty(name = "app.facturacion.proveedor", havingValue = "demostracion", matchIfMissing = true)
public class TimbradoDemostracion implements ServicioTimbrado {
    @Override public Resultado timbrar(Factura f) {
        Integer venta = f.getVenta().getId();
        UUID uuid = UUID.nameUUIDFromBytes(("MOCK-CFDI-" + venta + "-" + f.getIdempotencia()).getBytes(StandardCharsets.UTF_8));
        return new Resultado("TIMBRADA", uuid, null,
                "{\"simulado\":true,\"ventaId\":" + venta + ",\"aviso\":\"No es un CFDI timbrado por el SAT\"}", true);
    }
    @Override public String nombre() { return "demostracion"; }
}
