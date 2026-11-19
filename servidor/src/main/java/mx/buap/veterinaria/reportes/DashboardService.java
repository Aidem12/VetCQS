package mx.buap.veterinaria.reportes;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import mx.buap.veterinaria.comun.Reloj;

/**
 * Indicadores calculados con consultas agregadas en PostgreSQL (no se cargan tablas completas).
 * Ingresos = ventas PAGADAS menos devoluciones del periodo; se pueden reconciliar con las tablas venta y pago.
 */
@Service
public class DashboardService {
    private final NamedParameterJdbcTemplate jdbc;
    private final Reloj reloj;

    public DashboardService(NamedParameterJdbcTemplate jdbc, Reloj reloj) { this.jdbc = jdbc; this.reloj = reloj; }

    public record Filtros(LocalDate desde, LocalDate hasta, Integer usuarioId, Integer productoId, String tipo) {
        public Filtros {
            if (desde == null || hasta == null || hasta.isBefore(desde))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique un rango de fechas válido.");
            if (desde.plusDays(366).isBefore(hasta))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El rango máximo es de un año.");
            if (tipo != null && !tipo.isBlank() && !tipo.equals("PRODUCTO") && !tipo.equals("SERVICIO"))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El tipo debe ser PRODUCTO o SERVICIO.");
            if (tipo != null && tipo.isBlank()) tipo = null;
        }
    }
    public record Totales(BigDecimal ingresos, long ventas, BigDecimal ticketPromedio, BigDecimal devoluciones) {}
    public record Punto(LocalDate fecha, BigDecimal ingresos, long ventas) {}
    public record Etiquetado(String etiqueta, BigDecimal importe, long cantidad) {}
    public record Vendido(Integer productoId, String producto, String tipo, BigDecimal cantidad, BigDecimal importe) {}
    public record Caja(long turnos, long turnosConDiferencia, BigDecimal sumaDiferencias, BigDecimal salidasEfectivo) {}
    public record Alerta(String tipo, String descripcion, String detalle) {}
    public record Dashboard(Filtros filtros, Totales hoy, Totales semana, Totales mes, Totales periodo, List<Punto> serie,
            List<Etiquetado> metodosPago, List<Vendido> masVendidos, long consultas, List<Etiquetado> citasPorEstado,
            Caja caja, List<Alerta> alertas, long productosBajoMinimo, long lotesPorCaducar) {}

    @Transactional(readOnly = true)
    public Dashboard calcular(Filtros f) {
        LocalDate hoy = reloj.hoy();
        LocalDate lunes = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate primeroMes = hoy.withDayOfMonth(1);
        return new Dashboard(f,
                totales(hoy, hoy, f), totales(lunes, hoy, f), totales(primeroMes, hoy, f), totales(f.desde(), f.hasta(), f),
                serie(f), metodosPago(f), masVendidos(f), consultas(f), citas(f), caja(f), alertas(hoy),
                contar("select count(*) from producto where activo and tipo = 'PRODUCTO' and stock_actual <= stock_minimo", Map.of()),
                contar("select count(*) from lote where cantidad > 0 and caducidad <= :limite", Map.of("limite", hoy.plusDays(30))));
    }

    private MapSqlParameterSource params(LocalDate desde, LocalDate hasta, Filtros f) {
        return new MapSqlParameterSource()
                .addValue("desde", Timestamp.valueOf(desde.atStartOfDay()))
                .addValue("hasta", Timestamp.valueOf(hasta.plusDays(1).atStartOfDay()))
                .addValue("usuario", f.usuarioId(), java.sql.Types.INTEGER)
                .addValue("producto", f.productoId(), java.sql.Types.INTEGER)
                .addValue("tipo", f.tipo(), java.sql.Types.VARCHAR);
    }

    /** Con filtro de producto o tipo, el ingreso se mide por los renglones vendidos; sin filtro, por el total de la venta. */
    private Totales totales(LocalDate desde, LocalDate hasta, Filtros f) {
        boolean porRenglon = f.productoId() != null || f.tipo() != null;
        String sql = porRenglon ? """
                select coalesce(sum(d.importe), 0) as ingresos, count(distinct v.id) as ventas
                from venta v join venta_detalle d on d.venta_id = v.id join producto p on p.id = d.producto_id
                where v.estado = 'PAGADA' and v.fecha >= :desde and v.fecha < :hasta
                  and (cast(:usuario as int) is null or v.cajero_id = :usuario)
                  and (cast(:producto as int) is null or p.id = :producto)
                  and (cast(:tipo as varchar) is null or p.tipo = :tipo)"""
            : """
                select coalesce(sum(v.total), 0) as ingresos, count(*) as ventas
                from venta v
                where v.estado = 'PAGADA' and v.fecha >= :desde and v.fecha < :hasta
                  and (cast(:usuario as int) is null or v.cajero_id = :usuario)""";
        var p = params(desde, hasta, f);
        Map<String, Object> fila = jdbc.queryForMap(sql, p);
        BigDecimal ingresos = (BigDecimal) fila.get("ingresos");
        long ventas = ((Number) fila.get("ventas")).longValue();
        BigDecimal devoluciones = porRenglon ? BigDecimal.ZERO : jdbc.queryForObject("""
                select coalesce(sum(dv.monto), 0) from devolucion dv join venta v on v.id = dv.venta_id
                where dv.fecha >= :desde and dv.fecha < :hasta and (cast(:usuario as int) is null or v.cajero_id = :usuario)""",
                p, BigDecimal.class);
        BigDecimal neto = ingresos.subtract(devoluciones);
        BigDecimal promedio = ventas == 0 ? BigDecimal.ZERO : ingresos.divide(BigDecimal.valueOf(ventas), 2, java.math.RoundingMode.HALF_UP);
        return new Totales(neto, ventas, promedio, devoluciones);
    }

    private List<Punto> serie(Filtros f) {
        return jdbc.query("""
                select cast(v.fecha as date) as dia, coalesce(sum(v.total), 0) as ingresos, count(*) as ventas
                from venta v where v.estado = 'PAGADA' and v.fecha >= :desde and v.fecha < :hasta
                  and (cast(:usuario as int) is null or v.cajero_id = :usuario)
                group by cast(v.fecha as date) order by dia""", params(f.desde(), f.hasta(), f),
                (rs, i) -> new Punto(rs.getDate("dia").toLocalDate(), rs.getBigDecimal("ingresos"), rs.getLong("ventas")));
    }

    private List<Etiquetado> metodosPago(Filtros f) {
        return jdbc.query("""
                select pg.forma, coalesce(sum(pg.monto), 0) as importe, count(*) as cantidad
                from pago pg join venta v on v.id = pg.venta_id
                where v.estado = 'PAGADA' and v.fecha >= :desde and v.fecha < :hasta
                  and (cast(:usuario as int) is null or v.cajero_id = :usuario)
                group by pg.forma order by importe desc""", params(f.desde(), f.hasta(), f),
                (rs, i) -> new Etiquetado(rs.getString("forma"), rs.getBigDecimal("importe"), rs.getLong("cantidad")));
    }

    private List<Vendido> masVendidos(Filtros f) {
        return jdbc.query("""
                select p.id, p.nombre, p.tipo, sum(d.cantidad) as cantidad, sum(d.importe) as importe
                from venta v join venta_detalle d on d.venta_id = v.id join producto p on p.id = d.producto_id
                where v.estado = 'PAGADA' and v.fecha >= :desde and v.fecha < :hasta
                  and (cast(:usuario as int) is null or v.cajero_id = :usuario)
                  and (cast(:producto as int) is null or p.id = :producto)
                  and (cast(:tipo as varchar) is null or p.tipo = :tipo)
                group by p.id, p.nombre, p.tipo order by importe desc, cantidad desc limit 10""", params(f.desde(), f.hasta(), f),
                (rs, i) -> new Vendido(rs.getInt("id"), rs.getString("nombre"), rs.getString("tipo"),
                        rs.getBigDecimal("cantidad"), rs.getBigDecimal("importe")));
    }

    private long consultas(Filtros f) {
        return contar("select count(*) from consulta where fecha >= :desde and fecha < :hasta", params(f.desde(), f.hasta(), f).getValues());
    }

    private List<Etiquetado> citas(Filtros f) {
        return jdbc.query("""
                select estado, count(*) as cantidad from cita where inicio >= :desde and inicio < :hasta
                group by estado order by estado""", params(f.desde(), f.hasta(), f),
                (rs, i) -> new Etiquetado(rs.getString("estado"), BigDecimal.ZERO, rs.getLong("cantidad")));
    }

    private Caja caja(Filtros f) {
        var p = params(f.desde(), f.hasta(), f);
        Map<String, Object> t = jdbc.queryForMap("""
                select count(*) as turnos, count(*) filter (where diferencia is not null and diferencia <> 0) as con_diferencia,
                       coalesce(sum(diferencia), 0) as suma
                from turno where apertura >= :desde and apertura < :hasta
                  and (cast(:usuario as int) is null or cajero_id = :usuario)""", p);
        BigDecimal salidas = jdbc.queryForObject("""
                select coalesce(sum(s.monto), 0) from salida_efectivo s join turno t on t.id = s.turno_id
                where s.fecha >= :desde and s.fecha < :hasta and (cast(:usuario as int) is null or t.cajero_id = :usuario)""", p, BigDecimal.class);
        return new Caja(((Number) t.get("turnos")).longValue(), ((Number) t.get("con_diferencia")).longValue(),
                (BigDecimal) t.get("suma"), salidas);
    }

    private List<Alerta> alertas(LocalDate hoy) {
        List<Alerta> lista = new ArrayList<>(jdbc.query("""
                select nombre, stock_actual, stock_minimo from producto
                where activo and tipo = 'PRODUCTO' and stock_actual <= stock_minimo
                order by (stock_minimo - stock_actual) desc, nombre limit 10""", Map.of(),
                (rs, i) -> new Alerta("STOCK_BAJO", rs.getString("nombre"),
                        "Existencia " + rs.getBigDecimal("stock_actual").stripTrailingZeros().toPlainString()
                                + " / mínimo " + rs.getBigDecimal("stock_minimo").stripTrailingZeros().toPlainString())));
        lista.addAll(jdbc.query("""
                select p.nombre, l.numero_lote, l.caducidad, l.cantidad from lote l join producto p on p.id = l.producto_id
                where l.cantidad > 0 and l.caducidad <= :limite order by l.caducidad limit 10""",
                Map.of("limite", hoy.plusDays(30)),
                (rs, i) -> {
                    LocalDate cad = rs.getDate("caducidad").toLocalDate();
                    return new Alerta(cad.isBefore(hoy) ? "CADUCADO" : "POR_CADUCAR", rs.getString("nombre"),
                            "Lote " + rs.getString("numero_lote") + " · caduca " + cad + " · " + rs.getBigDecimal("cantidad").stripTrailingZeros().toPlainString() + " pzas");
                }));
        return lista;
    }

    private long contar(String sql, Map<String, ?> p) {
        Long n = jdbc.queryForObject(sql, p, Long.class);
        return n == null ? 0 : n;
    }
}
