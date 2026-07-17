package com.ejemplo.app.infraestructure.ordermanager.persistencia;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarSagasSoporte.FiltroSagas;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarSagasSoporte.PasoDetalle;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarSagasSoporte.SagaDetalle;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarSagasSoporte.SagaResumen;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoConsultaSagasSoporte;
import com.ejemplo.app.business.ordermanager.dominio.comun.AuditoriaIntervencion;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;
import com.ejemplo.app.business.ordermanager.dominio.comun.UsuarioSoporte;

/**
 * Modelo de lectura de la pantalla de soporte: queries SQL directas sobre
 * {@code orden}/{@code saga} (CQRS ligero), sin cargar agregados. El paso
 * pendiente, si requiere datos manuales y si es cancelable se derivan de
 * (tipo, estado) a través de la SPI {@link DescriptorSoporteOrden} (una
 * implementación por tipo, indexadas por {@link DescriptorSoporteOrden#tipo()}).
 */
@Component
public class AdaptadorConsultaSagasSoporte implements PuertoConsultaSagasSoporte {

    private final OrdenJpaRepository ordenes;
    private final SagaJpaRepository sagas;
    private final Map<TipoSaga, DescriptorSoporteOrden> descriptores;

    public AdaptadorConsultaSagasSoporte(OrdenJpaRepository ordenes, SagaJpaRepository sagas,
            List<DescriptorSoporteOrden> descriptores) {
        this.ordenes = ordenes;
        this.sagas = sagas;
        this.descriptores = descriptores.stream()
                .collect(Collectors.toUnmodifiableMap(DescriptorSoporteOrden::tipo, d -> d));
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
    public List<SagaDetalle> porExternalId(ExternalId externalId) {
        var filas = ordenes.porExternalId(externalId.valor().toString());
        return filas.stream()
                .map(fila -> detalle(TipoSaga.valueOf(fila.getTipo()), SagaId.de(fila.getSagaId())))
                .toList();
    }

    @Override
    public SagaDetalle detalle(TipoSaga tipo, SagaId id) {
        var sagaId = id.valor().toString();
        var fila = ordenes.resumenDe(tipo.name(), sagaId)
                .orElseThrow(() -> new IllegalArgumentException("No existe la orden " + sagaId));
        var resumen = resumenDe(fila);
        var descriptor = descriptorDe(tipo);
        var pendiente = descriptor.pasoPendiente(fila.getEstado());
        var pasos = pendiente == null ? List.<PasoDetalle>of()
                : List.of(new PasoDetalle(pendiente, descriptor.datosManualesObligatorios(fila.getEstado())));
        var auditoria = sagas.findById(sagaId).map(AdaptadorConsultaSagasSoporte::auditoriaDe).orElse(List.of());
        return new SagaDetalle(resumen, descriptor.cancelable(fila.getEstado()), pasos, auditoria);
    }

    private DescriptorSoporteOrden descriptorDe(TipoSaga tipo) {
        var descriptor = descriptores.get(tipo);
        if (descriptor == null) {
            throw new IllegalStateException("No hay DescriptorSoporteOrden registrado para el tipo " + tipo);
        }
        return descriptor;
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
}
