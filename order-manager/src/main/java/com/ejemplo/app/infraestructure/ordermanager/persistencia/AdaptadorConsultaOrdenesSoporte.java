package com.ejemplo.app.infraestructure.ordermanager.persistencia;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarOrdenesSoporte.FiltroOrdenes;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarOrdenesSoporte.PasoDetalle;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarOrdenesSoporte.OrdenDetalle;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarOrdenesSoporte.OrdenResumen;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoConsultaOrdenesSoporte;
import com.ejemplo.app.business.ordermanager.dominio.AuditoriaIntervencion;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.TipoOrden;
import com.ejemplo.app.business.ordermanager.dominio.UsuarioSoporte;

/**
 * Modelo de lectura de la pantalla de soporte: queries SQL directas sobre
 * {@code orden}/{@code proceso} (CQRS ligero), sin cargar agregados. El paso
 * pendiente, si requiere datos manuales y si es cancelable se derivan de
 * (tipo, estado) a través de la SPI {@link DescriptorSoporteOrden} (una
 * implementación por tipo, indexadas por {@link DescriptorSoporteOrden#tipo()}).
 */
@Component
public class AdaptadorConsultaOrdenesSoporte implements PuertoConsultaOrdenesSoporte {

    private final OrdenJpaRepository ordenes;
    private final ProcesoJpaRepository procesos;
    private final Map<TipoOrden, DescriptorSoporteOrden> descriptores;

    public AdaptadorConsultaOrdenesSoporte(OrdenJpaRepository ordenes, ProcesoJpaRepository procesos,
            List<DescriptorSoporteOrden> descriptores) {
        this.ordenes = ordenes;
        this.procesos = procesos;
        this.descriptores = descriptores.stream()
                .collect(Collectors.toUnmodifiableMap(DescriptorSoporteOrden::tipo, d -> d));
    }

    @Override
    public List<OrdenResumen> ordenesBloqueadas() {
        return ordenes.ordenesBloqueadas().stream().map(AdaptadorConsultaOrdenesSoporte::resumenDe).toList();
    }

    @Override
    public List<OrdenResumen> ordenesEnEjecucion() {
        return ordenes.ordenesEnEjecucion(Instant.now()).stream()
                .map(AdaptadorConsultaOrdenesSoporte::resumenDe).toList();
    }

    @Override
    public List<OrdenResumen> ordenesConTicketPendiente() {
        return ordenes.ordenesConTicketPendiente().stream().map(AdaptadorConsultaOrdenesSoporte::resumenDe).toList();
    }

    @Override
    public List<OrdenResumen> buscar(FiltroOrdenes filtro) {
        return ordenes.buscar(filtro.estado(), filtro.iniciadaDesde(), filtro.iniciadaHasta(),
                filtro.actualizadaDesde(), filtro.actualizadaHasta()).stream()
                .map(AdaptadorConsultaOrdenesSoporte::resumenDe).toList();
    }

    @Override
    public List<OrdenDetalle> porExternalId(ExternalId externalId) {
        var filas = ordenes.porExternalId(externalId.valor().toString());
        return filas.stream()
                .map(fila -> detalle(new TipoOrden(fila.getTipo()), OrdenId.de(fila.getOrdenId())))
                .toList();
    }

    @Override
    public OrdenDetalle detalle(TipoOrden tipo, OrdenId id) {
        var ordenId = id.valor();
        var fila = ordenes.resumenDe(tipo.valor(), ordenId)
                .orElseThrow(() -> new IllegalArgumentException("No existe la orden " + ordenId));
        var resumen = resumenDe(fila);
        var descriptor = descriptorDe(tipo);
        var pendiente = descriptor.pasoPendiente(fila.getEstado());
        var pasos = pendiente == null ? List.<PasoDetalle>of()
                : List.of(new PasoDetalle(pendiente, descriptor.datosManualesObligatorios(fila.getEstado())));
        var auditoria = procesos.findById(ordenId).map(AdaptadorConsultaOrdenesSoporte::auditoriaDe).orElse(List.of());
        return new OrdenDetalle(resumen, descriptor.cancelable(fila.getEstado()), pasos, auditoria);
    }

    private DescriptorSoporteOrden descriptorDe(TipoOrden tipo) {
        var descriptor = descriptores.get(tipo);
        if (descriptor == null) {
            throw new IllegalStateException("No hay DescriptorSoporteOrden registrado para el tipo " + tipo);
        }
        return descriptor;
    }

    private static OrdenResumen resumenDe(OrdenResumenFila f) {
        return new OrdenResumen(OrdenId.de(f.getOrdenId()), new TipoOrden(f.getTipo()),
                ExternalId.de(f.getExternalId()), f.getEstado(), f.getIntentos(), instanteONull(f.getTicketAbiertoEn()),
                instanteONull(f.getProximoReintentoEn()), instanteONull(f.getIniciadaEn()),
                instanteONull(f.getActualizadaEn()));
    }

    private static Instant instanteONull(OffsetDateTime valor) {
        return valor == null ? null : valor.toInstant();
    }

    private static List<AuditoriaIntervencion> auditoriaDe(ProcesoEntity entity) {
        return entity.getAuditoria().stream()
                .map(a -> new AuditoriaIntervencion(a.getCuando(), new UsuarioSoporte(a.getQuien()),
                        a.getAccion(), a.getDetalle()))
                .toList();
    }
}
