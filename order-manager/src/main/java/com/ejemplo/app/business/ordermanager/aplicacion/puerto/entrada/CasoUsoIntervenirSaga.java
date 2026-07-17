package com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada;

import java.util.Map;

import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoOrden;
import com.ejemplo.app.business.ordermanager.dominio.comun.UsuarioSoporte;

/**
 * Acciones de la pantalla de soporte. Lo invoca el adaptador REST del
 * backoffice.
 *
 * Cada saga tiene en todo momento como mucho UN paso pendiente (el que marca
 * su FSM de negocio): ya no hace falta identificarlo por su enum. El
 * parámetro {@code nombrePaso} se conserva por compatibilidad de la firma y
 * como dato informativo para la auditoría, pero es opcional (puede ir null o
 * ignorarse) y no se usa para resolver a qué paso concreto se interviene.
 */
public interface CasoUsoIntervenirSaga {

    /** Reintento manual del paso pendiente actual: resetea la escalera de intentos y lo relanza. */
    void reintentarPaso(TipoOrden tipo, SagaId id, String nombrePaso, UsuarioSoporte quien);

    /**
     * Soporte arregló a mano el paso pendiente actual en el sistema destino y
     * lo marca OK. datosManuales: los datos que el paso habría producido, si
     * algún paso posterior los consume (p. ej. INICIO de la secundaria 1 ->
     * "refInicio"). La saga continúa su FSM desde ahí.
     */
    void marcarPasoOk(TipoOrden tipo, SagaId id, String nombrePaso, UsuarioSoporte quien,
                      String justificacion, Map<String, String> datosManuales);
}
