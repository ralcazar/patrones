package com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada;

import java.util.Map;

import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.TipoOrden;
import com.ejemplo.app.business.ordermanager.dominio.UsuarioSoporte;

/**
 * Acciones de la pantalla de soporte. Lo invoca el adaptador REST del
 * backoffice.
 *
 * Cada orden tiene en todo momento como mucho UN paso pendiente (el que marca
 * su FSM de negocio): no hace falta identificarlo por nombre, se resuelve
 * siempre sobre ese paso pendiente actual.
 */
public interface CasoUsoIntervenirOrden {

    /** Reintento manual del paso pendiente actual: resetea la escalera de intentos y lo relanza. */
    void reintentarPaso(TipoOrden tipo, OrdenId id, UsuarioSoporte quien);

    /**
     * Soporte arregló a mano el paso pendiente actual en el sistema destino y
     * lo marca OK. datosManuales: los datos que el paso habría producido, si
     * algún paso posterior los consume (p. ej. INICIO de la secundaria 1 ->
     * "refInicio"). La orden continúa su FSM desde ahí.
     */
    void marcarPasoOk(TipoOrden tipo, OrdenId id, UsuarioSoporte quien,
                      String justificacion, Map<String, String> datosManuales);
}
