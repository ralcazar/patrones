package com.ejemplo.tramitacion.aplicacion.saga.puerto.entrada;

import java.util.Map;

import com.ejemplo.tramitacion.dominio.saga.general.Paso;
import com.ejemplo.tramitacion.dominio.saga.general.SagaId;
import com.ejemplo.tramitacion.dominio.saga.general.TipoSaga;
import com.ejemplo.tramitacion.dominio.saga.general.UsuarioSoporte;

/** Acciones de la pantalla de soporte. Lo invoca el adaptador REST del backoffice. */
public interface CasoUsoIntervenirSaga {

    /**
     * Cancela la saga principal (compensando PASO2 y PASO1). Solo posible antes
     * de que PASO7 complete; después lanza PuntoNoRetornoSuperadoException.
     */
    void cancelarPrincipal(SagaId id, UsuarioSoporte quien, String motivo);

    /** Reintento manual de un paso bloqueado: resetea el contador y relanza. */
    void reintentarPaso(TipoSaga tipo, SagaId id, Paso paso, UsuarioSoporte quien);

    /**
     * Soporte arregló el paso a mano en el sistema destino y lo marca OK.
     * datosManuales: los datos que el paso habría producido, si algún paso
     * posterior los consume (p. ej. SECUENCIAL1 -> "refSecuencial1"). La saga continúa.
     */
    void marcarPasoOk(TipoSaga tipo, SagaId id, Paso paso, UsuarioSoporte quien,
                      String justificacion, Map<String, String> datosManuales);
}
