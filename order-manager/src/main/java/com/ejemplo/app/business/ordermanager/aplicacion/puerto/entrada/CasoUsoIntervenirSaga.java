package com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada;

import java.util.Map;

import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;
import com.ejemplo.app.business.ordermanager.dominio.comun.UsuarioSoporte;

/**
 * Acciones de la pantalla de soporte. Lo invoca el adaptador REST del
 * backoffice, que manda el paso por su NOMBRE: el servicio lo resuelve al
 * enum concreto de la saga indicada por el tipo.
 */
public interface CasoUsoIntervenirSaga {

    /**
     * Cancela la saga principal (compensando PASO2 y PASO1). Solo posible antes
     * de que PASO7 complete; después lanza PuntoNoRetornoSuperadoException.
     */
    void cancelarPrincipal(SagaId id, UsuarioSoporte quien, String motivo);

    /** Reintento manual de un paso bloqueado: resetea el contador y relanza. */
    void reintentarPaso(TipoSaga tipo, SagaId id, String nombrePaso, UsuarioSoporte quien);

    /**
     * Soporte arregló el paso a mano en el sistema destino y lo marca OK.
     * datosManuales: los datos que el paso habría producido, si algún paso
     * posterior los consume (p. ej. INICIO de la secundaria 1 -> "refInicio").
     * La saga continúa.
     */
    void marcarPasoOk(TipoSaga tipo, SagaId id, String nombrePaso, UsuarioSoporte quien,
                      String justificacion, Map<String, String> datosManuales);
}
