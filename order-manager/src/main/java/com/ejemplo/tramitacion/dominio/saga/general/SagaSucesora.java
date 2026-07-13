package com.ejemplo.tramitacion.dominio.saga.general;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;

/**
 * Base común de las 3 sagas que arrancan cuando la saga principal completa
 * (ASINCRONA, SECUENCIAL, SIMPLE). "Sucesora" describe el CUÁNDO arrancan,
 * no una jerarquía: nacen con su propio contexto recortado (ContextoArranque),
 * son independientes entre sí y de la principal, y no hay join — cada una
 * termina sola.
 *
 * Como nacen DESPUÉS del punto de no retorno de la tramitación:
 * - Nunca se cancelan ni compensan (esCancelable() hereda el false de Saga).
 * - Ante fallo: backoff exponencial hasta agotar intentos, luego ticket a soporte.
 */
public abstract sealed class SagaSucesora extends Saga
        permits SagaAsincrona, SagaSecuencial, SagaSimple {

    protected final DatoNegocio1Id datoNegocio1Id;

    protected SagaSucesora(SagaId id, DatoNegocio1Id datoNegocio1Id,
                           EnumSet<Paso> misPasos, EstadoSaga estado, long version) {
        super(id, misPasos, estado, version);
        this.datoNegocio1Id = datoNegocio1Id;
    }

    /** Constructor de rehidratación para el adaptador de persistencia. */
    protected SagaSucesora(SagaId id, DatoNegocio1Id datoNegocio1Id,
                           EnumMap<Paso, EjecucionPaso> pasos, List<AuditoriaIntervencion> auditoria,
                           EstadoSaga estado, long version) {
        super(id, pasos, auditoria, estado, version);
        this.datoNegocio1Id = datoNegocio1Id;
    }

    @Override
    public final DatoNegocio1Id datoNegocio1Id() {
        return datoNegocio1Id;
    }
}
