package com.ejemplo.tramitacion.aplicacion.saga.puerto.entrada;

import java.time.Instant;
import java.util.List;

import com.ejemplo.tramitacion.dominio.saga.general.AuditoriaIntervencion;
import com.ejemplo.tramitacion.dominio.saga.general.EstadoPaso;
import com.ejemplo.tramitacion.dominio.saga.general.EstadoSaga;
import com.ejemplo.tramitacion.dominio.saga.general.DatoNegocio1Id;
import com.ejemplo.tramitacion.dominio.saga.general.MotivoFallo;
import com.ejemplo.tramitacion.dominio.saga.general.Paso;
import com.ejemplo.tramitacion.dominio.saga.general.SagaId;
import com.ejemplo.tramitacion.dominio.saga.general.TipoSaga;

/**
 * Consultas para la pantalla de soporte. Es un modelo de lectura: el adaptador
 * lo resuelve con queries sobre las tablas de las sagas, sin pasar por los agregados.
 */
public interface CasoUsoConsultarSagasSoporte {

    /** Bandeja de trabajo: todas las sagas con algún paso bloqueado. */
    List<SagaResumen> sagasBloqueadas();

    /** Todas las sagas en ejecución (INICIADA o EN_CURSO). */
    List<SagaResumen> sagasEnEjecucion();

    /** Búsqueda con filtros combinables; los criterios a null no aplican. */
    List<SagaResumen> buscar(FiltroSagas filtro);

    /** Vista de conjunto de un datoNegocio1: todas sus sagas. */
    VistaDatoNegocio1 vistaDatoNegocio1(DatoNegocio1Id datoNegocio1Id);

    SagaDetalle detalle(TipoSaga tipo, SagaId id);

    /** Criterios de la pantalla de soporte: estado, fecha de inicio y fecha de última actualización. */
    record FiltroSagas(EstadoSaga estado,
                       Instant iniciadaDesde, Instant iniciadaHasta,
                       Instant actualizadaDesde, Instant actualizadaHasta) {

        public static FiltroSagas porEstado(EstadoSaga estado) {
            return new FiltroSagas(estado, null, null, null, null);
        }

        public static FiltroSagas iniciadaEntre(Instant desde, Instant hasta) {
            return new FiltroSagas(null, desde, hasta, null, null);
        }

        public static FiltroSagas actualizadaEntre(Instant desde, Instant hasta) {
            return new FiltroSagas(null, null, null, desde, hasta);
        }
    }

    record SagaResumen(SagaId id, TipoSaga tipo, DatoNegocio1Id datoNegocio1Id,
                       EstadoSaga estado, boolean tienePasosBloqueados,
                       Instant iniciadaEn, Instant actualizadaEn) {}

    record PasoDetalle(Paso paso, EstadoPaso estado, int intentos, MotivoFallo ultimoFallo,
                       boolean datosManualesObligatorios) {}

    record SagaDetalle(SagaResumen resumen, boolean cancelable,
                       List<PasoDetalle> pasos, List<AuditoriaIntervencion> auditoria) {}

    record VistaDatoNegocio1(DatoNegocio1Id datoNegocio1Id, SagaDetalle principal,
                           List<SagaDetalle> sucesoras) {}
}
