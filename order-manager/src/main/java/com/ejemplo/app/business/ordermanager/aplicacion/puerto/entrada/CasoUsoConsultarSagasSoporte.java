package com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada;

import java.time.Instant;
import java.util.List;

import com.ejemplo.app.business.ordermanager.dominio.comun.AuditoriaIntervencion;
import com.ejemplo.app.business.ordermanager.dominio.comun.EstadoPaso;
import com.ejemplo.app.business.ordermanager.dominio.comun.EstadoSaga;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.comun.MotivoFallo;
import com.ejemplo.app.business.ordermanager.dominio.comun.PasoSaga;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;

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

    /** Vista de conjunto de una tramitación: sus 4 sagas, correlacionadas por externalId. */
    VistaTramitacion vistaTramitacion(ExternalId externalId);

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

    record SagaResumen(SagaId id, TipoSaga tipo, ExternalId externalId,
                       EstadoSaga estado, boolean tienePasosBloqueados,
                       Instant iniciadaEn, Instant actualizadaEn) {}

    record PasoDetalle(PasoSaga paso, EstadoPaso estado, int intentos, MotivoFallo ultimoFallo,
                       boolean datosManualesObligatorios) {}

    record SagaDetalle(SagaResumen resumen, boolean cancelable,
                       List<PasoDetalle> pasos, List<AuditoriaIntervencion> auditoria) {}

    record VistaTramitacion(ExternalId externalId, SagaDetalle principal,
                            List<SagaDetalle> secundarias) {}
}
