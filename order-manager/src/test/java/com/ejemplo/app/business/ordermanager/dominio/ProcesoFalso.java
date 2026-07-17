package com.ejemplo.app.business.ordermanager.dominio;

import java.util.List;
import java.util.Map;

/**
 * Doble de {@link Proceso} para los tests del motor: un tipo de orden nuevo,
 * registrado con {@link #TIPO}, que no existía cuando se escribió el motor.
 * Demuestra que ordermanager no necesita conocer tipos concretos como los de
 * business.sagas para poder ejecutarse.
 */
public final class ProcesoFalso extends Proceso<ProcesoFalso.Estado> {

    public static final TipoOrden TIPO = new TipoOrden("FALSO");

    public enum Estado { INICIAL, TERMINADO }

    private ProcesoFalso(OrdenId id, ExternalId externalId, Estado estado, List<AuditoriaIntervencion> auditoria) {
        super(id, externalId, estado, auditoria);
    }

    public static ProcesoFalso crear(OrdenId id, ExternalId externalId) {
        return new ProcesoFalso(id, externalId, Estado.INICIAL, List.of());
    }

    public static ProcesoFalso rehidratar(OrdenId id, ExternalId externalId, Estado estado,
            List<AuditoriaIntervencion> auditoria) {
        return new ProcesoFalso(id, externalId, estado, auditoria);
    }

    @Override
    public TipoOrden tipo() { return TIPO; }

    @Override
    public ComandoPaso comandoActual() { return null; }

    @Override
    public void aplicarYAvanzar(ResultadoPaso resultado) {
        this.estado = Estado.TERMINADO;
    }

    @Override
    public boolean terminada() { return estado == Estado.TERMINADO; }

    @Override
    public ResultadoOrden resultadoFinal() { return ResultadoOrden.FINALIZADA_OK; }

    @Override
    public void marcarPasoActualOkManual(UsuarioSoporte quien, String justificacion, Map<String, String> datos) {
        this.estado = Estado.TERMINADO;
    }
}
