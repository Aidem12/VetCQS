package mx.buap.veterinaria.caja;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import mx.buap.veterinaria.comun.Reloj;
import mx.buap.veterinaria.config.WebSocketConfig.AvisosCaja;

/** Guarda el aviso y, cuando la transacción se confirma, lo empuja por WebSocket a las cajas conectadas. */
@Service
public class AvisosCajaService {
    private final AvisoCajaRepository avisos;
    private final AvisosCaja canal;
    private final ObjectMapper json;
    private final Reloj reloj;

    public AvisosCajaService(AvisoCajaRepository avisos, AvisosCaja canal, ObjectMapper json, Reloj reloj) {
        this.avisos = avisos; this.canal = canal; this.json = json; this.reloj = reloj;
    }

    public record Aviso(Integer id, String tipo, Integer ventaId, String mensaje, String creadoEn) {
        static Aviso de(AvisoCaja a) { return new Aviso(a.getId(), a.getTipo(), a.getVentaId(), a.getMensaje(), a.getCreadoEn().toString()); }
    }

    @Transactional
    public Aviso publicar(String tipo, Integer ventaId, String mensaje) {
        Aviso aviso = Aviso.de(avisos.save(new AvisoCaja(tipo, ventaId, mensaje, reloj.ahora())));
        String texto;
        try { texto = json.writeValueAsString(Map.of("tipo", "AVISO", "aviso", aviso)); }
        catch (JsonProcessingException e) { return aviso; }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { canal.avisar(texto); }
            });
        } else {
            canal.avisar(texto);
        }
        return aviso;
    }

    /** Avisos posteriores a {@code desde}; con desde = 0 devuelve los 20 más recientes en orden. */
    @Transactional(readOnly = true)
    public List<Aviso> desde(int desde) {
        if (desde <= 0) {
            List<Aviso> lista = new ArrayList<>(avisos.findTop20ByOrderByIdDesc().stream().map(Aviso::de).toList());
            Collections.reverse(lista);
            return lista;
        }
        return avisos.findTop50ByIdGreaterThanOrderByIdAsc(desde).stream().map(Aviso::de).toList();
    }
}
