package com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida;

import java.time.Duration;

import com.ejemplo.app.business.ordermanager.dominio.DetalleError;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.TipoOrden;

/**
 * Observa los momentos relevantes del bucle de ejecución del motor (ver
 * {@code ServicioContinuarOrden}) y la limpieza de datos antiguos, para que
 * una herramienta externa (log estructurado, métricas) los agregue sin que
 * la capa de aplicación dependa de ningún framework de logging: vocabulario
 * neutro del motor, ni rastro de "saga" aquí.
 *
 * "Ticket abierto" NO vive en este puerto: nace directamente en el adaptador
 * de salida de tickets (ya en infraestructura, ver AdaptadorTicketsLog), así
 * que no hace falta atravesar la capa de aplicación para observarlo.
 */
public interface PuertoObservadorEjecucion {

    /** Un pod reclamó el token con éxito y va a ejecutar pasos sobre la orden. */
    void reclamoGanado(OrdenId id, TipoOrden tipo);

    /** El pod no pudo reclamar el token: alguien más lo tiene, o la orden ya terminó. */
    void reclamoPerdido(OrdenId id, TipoOrden tipo, MotivoReclamoPerdido motivo);

    /**
     * Un guardado del agregado chocó por versión (otro actor escribió entre
     * medias) en la operación indicada ({@code reclamarToken},
     * {@code ejecutarPaso} o {@code programarReintento}).
     */
    void colisionOptimista(OrdenId id, TipoOrden tipo, String operacion);

    /** {@code ProcesadorOrden.ejecutarPaso} terminó sin lanzar, con la duración medida por el llamante. */
    void pasoCompletado(OrdenId id, TipoOrden tipo, long duracionMs);

    /** {@code ProcesadorOrden.ejecutarPaso} lanzó; {@code intento} es el nº de fallo acumulado tras este. */
    void pasoFallido(OrdenId id, TipoOrden tipo, int intento, DetalleError error);

    /** Se programó un reintento según la escalera de {@code PoliticaReintentos}. */
    void reintentoProgramado(OrdenId id, TipoOrden tipo, int intento, Duration espera);

    /** La orden quedó a la espera de un evento externo ({@code SenalPaso.Aparcar}). */
    void ordenAparcada(OrdenId id, TipoOrden tipo, Duration ventana);

    /**
     * La orden terminó ({@code SenalPaso.Finalizada}). En el diseño actual del
     * motor la finalización siempre es éxito: los fallos nunca agotan la
     * escalera de reintentos (se repiten indefinidamente cada 180 min con
     * ticket abierto), así que no existe una "finalización con error" real
     * que observar.
     */
    void ordenFinalizada(OrdenId id, TipoOrden tipo);

    /** Barrido de limpieza de datos antiguos: cuántas órdenes se purgaron. */
    void datosAntiguosPurgados(long ordenesEliminadas);

    /** Motivo por el que un intento de reclamo no consigue el token. */
    enum MotivoReclamoPerdido {
        TOKEN_VIGENTE,
        NO_VIVA,
        /**
         * La orden no está en turno de ejecución ({@code OrdenRoot.turnoVencido}
         * es falso) sobre la fila recién cargada: otro actor la aparcó (o
         * programó un reintento futuro) entre el barrido de candidatas y este
         * reclamo. Ocurre incluso con lectura consistente del agregado.
         */
        NO_EJECUTABLE,
        COLISION_OPTIMISTA
    }
}
