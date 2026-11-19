package mx.buap.veterinaria.expediente;

import mx.buap.veterinaria.model.Usuario;

/** Registros clínicos que no se borran: se anulan con motivo y responsable. */
public interface Anulable {
    boolean isAnulada();
    void anular(Usuario por, String motivo);
}
