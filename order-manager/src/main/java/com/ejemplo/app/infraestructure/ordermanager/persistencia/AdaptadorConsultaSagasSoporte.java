package com.ejemplo.app.infraestructure.ordermanager.persistencia;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarSagasSoporte.FiltroSagas;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarSagasSoporte.PasoDetalle;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarSagasSoporte.SagaDetalle;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarSagasSoporte.SagaResumen;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarSagasSoporte.VistaTramitacion;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoConsultaSagasSoporte;
import com.ejemplo.app.business.ordermanager.dominio.comun.AuditoriaIntervencion;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;
import com.ejemplo.app.business.ordermanager.dominio.comun.UsuarioSoporte;

/**
 * Modelo de lectura de la pantalla de soporte: queries SQL directas sobre
 * {@code orden}/{@code saga} (CQRS ligero), sin cargar agregados. El paso
 * pendiente y si es cancelable se derivan de (tipo, estado) con la misma
 * tabla que la FSM de cada Saga, duplicada aquí a propósito: el dominio
 * no expone esa forma internamente y la lectura no debe cargar agregados.
 */
@Component
public class AdaptadorConsultaSagasSoporte implements PuertoConsultaSagasSoporte {

    private final OrdenJpaRepository ordenes;
    private final SagaJpaRepository sagas;

    public AdaptadorConsultaSagasSoporte(OrdenJpaRepository ordenes, SagaJpaRepository sagas) {
        this.ordenes = ordenes;
        this.sagas = sagas;
    }

    @Override
    public List<SagaResumen> sagasBloqueadas() {
        return ordenes.sagasBloqueadas().stream().map(AdaptadorConsultaSagasSoporte::resumenDe).toList();
    }

    @Override
    public List<SagaResumen> sagasEnEjecucion() {
        return ordenes.sagasEnEjecucion(Instant.now()).stream()
                .map(AdaptadorConsultaSagasSoporte::resumenDe).toList();
    }

    @Override
    public List<SagaResumen> sagasConTicketPendiente() {
        return ordenes.sagasConTicketPendiente().stream().map(AdaptadorConsultaSagasSoporte::resumenDe).toList();
    }

    @Override
    public List<SagaResumen> buscar(FiltroSagas filtro) {
        return ordenes.buscar(filtro.estado(), filtro.iniciadaDesde(), filtro.iniciadaHasta(),
                filtro.actualizadaDesde(), filtro.actualizadaHasta()).stream()
                .map(AdaptadorConsultaSagasSoporte::resumenDe).toList();
    }

    @Override
    public VistaTramitacion vistaTramitacion(ExternalId externalId) {
        var filas = ordenes.porExternalId(externalId.valor().toString());
        SagaDetalle principal = null;
        var secundarias = new ArrayList<SagaDetalle>();
        for (var fila : filas) {
            var tipo = TipoSaga.valueOf(fila.getTipo());
            var detalle = detalle(tipo, SagaId.de(fila.getSagaId()));
            if (tipo == TipoSaga.PRINCIPAL) {
                principal = detalle;
            } else {
                secundarias.add(detalle);
            }
        }
        return new VistaTramitacion(externalId, principal, secundarias);
    }

    @Override
    public SagaDetalle detalle(TipoSaga tipo, SagaId id) {
        var sagaId = id.valor().toString();
        var fila = ordenes.resumenDe(tipo.name(), sagaId)
                .orElseThrow(() -> new IllegalArgumentException("No existe la orden " + sagaId));
        var resumen = resumenDe(fila);
        var pendiente = pasoPendiente(tipo, fila.getEstado());
        var pasos = pendiente == null ? List.<PasoDetalle>of()
                : List.of(new PasoDetalle(pendiente, datosManualesObligatorios(tipo, fila.getEstado())));
        var auditoria = sagas.findById(sagaId).map(AdaptadorConsultaSagasSoporte::auditoriaDe).orElse(List.of());
        return new SagaDetalle(resumen, cancelable(tipo, fila.getEstado()), pasos, auditoria);
    }

    private static SagaResumen resumenDe(SagaResumenFila f) {
        return new SagaResumen(SagaId.de(f.getSagaId()), TipoSaga.valueOf(f.getTipo()),
                ExternalId.de(f.getExternalId()), f.getEstado(), f.getIntentos(), f.getTicketAbiertoEn(),
                f.getProximoReintentoEn(), f.getIniciadaEn(), f.getActualizadaEn());
    }

    private static List<AuditoriaIntervencion> auditoriaDe(SagaEntity entity) {
        return entity.getAuditoria().stream()
                .map(a -> new AuditoriaIntervencion(a.getCuando(), new UsuarioSoporte(a.getQuien()),
                        a.getAccion(), a.getDetalle()))
                .toList();
    }

    /** El paso pendiente (null si la saga ya no avanza: terminada o en compensación). */
    private static String pasoPendiente(TipoSaga tipo, String estado) {
        return switch (tipo) {
            case PRINCIPAL -> switch (estado) {
                case "INICIAL" -> "PASO1";
                case "PASO1_HECHO" -> "PASO2";
                case "PASO2_HECHO" -> "PASO3";
                case "PASO3_HECHO" -> "PASO4";
                case "PASO4_HECHO" -> "PASO5";
                case "PASO5_HECHO" -> "PASO6";
                case "PASO6_HECHO" -> "PASO7";
                case "PASO7_HECHO" -> "PASO8";
                default -> null;
            };
            case SECUNDARIA1 -> switch (estado) {
                case "INICIAL" -> "INICIO";
                case "INICIO_HECHO" -> "CONFIRMACION";
                default -> null;
            };
            case SECUNDARIA2 -> "INICIAL".equals(estado) || "ESPERANDO_RESPUESTA".equals(estado)
                    ? "SOLICITUD" : null;
            case SECUNDARIA3 -> "INICIAL".equals(estado) ? "EJECUCION" : null;
        };
    }

    private static boolean datosManualesObligatorios(TipoSaga tipo, String estado) {
        return switch (tipo) {
            case PRINCIPAL -> switch (estado) {
                case "INICIAL", "PASO1_HECHO", "PASO3_HECHO", "PASO4_HECHO", "PASO6_HECHO" -> true;
                default -> false;
            };
            case SECUNDARIA1 -> "INICIAL".equals(estado);
            case SECUNDARIA2, SECUNDARIA3 -> false;
        };
    }

    private static boolean cancelable(TipoSaga tipo, String estado) {
        if (tipo != TipoSaga.PRINCIPAL) {
            return false;
        }
        return switch (estado) {
            case "INICIAL", "PASO1_HECHO", "PASO2_HECHO", "PASO3_HECHO",
                    "PASO4_HECHO", "PASO5_HECHO", "PASO6_HECHO" -> true;
            default -> false;
        };
    }
}
