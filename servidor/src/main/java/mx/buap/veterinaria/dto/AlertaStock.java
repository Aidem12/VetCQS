package mx.buap.veterinaria.dto;
import java.math.BigDecimal;
import mx.buap.veterinaria.model.Producto;
/** Producto activo cuya existencia está en o por debajo de su stock mínimo (RF-14). */
public record AlertaStock(Integer id, String nombre, String codigoBarras,
        BigDecimal stockActual, BigDecimal stockMinimo, BigDecimal faltante) {
    public static AlertaStock de(Producto p) {
        BigDecimal faltante = p.getStockMinimo().subtract(p.getStockActual()).max(BigDecimal.ZERO);
        return new AlertaStock(p.getId(), p.getNombre(), p.getCodigoBarras(),
                p.getStockActual(), p.getStockMinimo(), faltante);
    }
}
