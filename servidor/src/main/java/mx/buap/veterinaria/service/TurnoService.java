package mx.buap.veterinaria.service;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import mx.buap.veterinaria.dto.*;
import mx.buap.veterinaria.model.*;
import mx.buap.veterinaria.repository.*;

@Service
public class TurnoService {
    private final TurnoRepository turnos; private final UsuarioRepository usuarios; private final PagoConsultaRepository pagos;
    public TurnoService(TurnoRepository t, UsuarioRepository u, PagoConsultaRepository p) { turnos=t; usuarios=u; pagos=p; }
    @Transactional
    public Turno abrir(TurnoAperturaRequest r) {
        if (turnos.findByCierreIsNull().isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un turno abierto");
        Usuario u = usuarios.findById(r.cajeroId()).filter(Usuario::isActivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cajero inactivo o inexistente"));
        Turno t = new Turno(); t.setCajero(u); t.setFondoInicial(r.fondoInicial()); return turnos.save(t);
    }
    @Transactional
    public Map<String,Object> cerrar(TurnoCierreRequest r) {
        Turno t = turnos.findOpenForUpdate().orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No hay turno abierto"));
        BigDecimal esperado = t.getFondoInicial().add(pagos.efectivoDelTurno(t.getId()));
        t.cerrar(r.efectivoContado(), esperado); turnos.save(t);
        return Map.of("id", t.getId(), "efectivoEsperado", esperado, "efectivoContado", r.efectivoContado(),
                "diferencia", r.efectivoContado().subtract(esperado), "cierre", t.getCierre());
    }
    public Turno abierto() { return turnos.findByCierreIsNull().orElse(null); }
}
