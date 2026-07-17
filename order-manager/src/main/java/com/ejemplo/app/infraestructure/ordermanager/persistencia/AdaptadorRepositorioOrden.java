package com.ejemplo.app.infraestructure.ordermanager.persistencia;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.dominio.comun.AuditoriaIntervencion;
import com.ejemplo.app.business.ordermanager.dominio.comun.ConcurrenciaOptimistaException;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.comun.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.comun.ResultadoOrden;
import com.ejemplo.app.business.ordermanager.dominio.comun.Saga;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoOrden;
import com.ejemplo.app.business.ordermanager.dominio.comun.UsuarioSoporte;

/**
 * ÚNICO adaptador de escritura del agregado: OrdenRoot (que contiene su
 * Saga). Mapea dominio&lt;-&gt;entidades JPA despachando la subclase de
 * Saga por {@code tipo} a través de la SPI {@link MapeadorProceso} (una
 * implementación por tipo, indexadas por {@link MapeadorProceso#tipo()}), y
 * traduce el conflicto de versión de Hibernate a
 * {@link ConcurrenciaOptimistaException}.
 */
@Component
public class AdaptadorRepositorioOrden implements RepositorioOrden {

    private final OrdenJpaRepository ordenes;
    private final SagaJpaRepository sagas;
    private final Map<TipoOrden, MapeadorProceso> mapeadores;

    public AdaptadorRepositorioOrden(OrdenJpaRepository ordenes, SagaJpaRepository sagas,
            List<MapeadorProceso> mapeadores) {
        this.ordenes = ordenes;
        this.sagas = sagas;
        this.mapeadores = mapeadores.stream()
                .collect(Collectors.toUnmodifiableMap(MapeadorProceso::tipo, m -> m));
    }

    @Override
    public void crear(OrdenRoot orden) {
        sagas.save(entidadSagaDe(orden.saga()));
        ordenes.save(entidadOrdenDe(orden));
    }

    @Override
    public OrdenRoot cargar(SagaId id) {
        var sagaId = id.valor().toString();
        var sagaEntity = sagas.findById(sagaId)
                .orElseThrow(() -> new IllegalArgumentException("No existe la saga " + sagaId));
        var ordenEntity = ordenes.findById(sagaId)
                .orElseThrow(() -> new IllegalArgumentException("No existe la orden " + sagaId));
        return OrdenRoot.rehidratar(sagaDesde(sagaEntity), ordenEntity.getIntentos(),
                ordenEntity.getProximoReintentoEn(), uuidONull(ordenEntity.getTokenTrabajador()),
                ordenEntity.getTokenExpiraEn(), ordenEntity.getTicketAbiertoEn(),
                resultadoONull(ordenEntity.getResultado()), ordenEntity.getVersion());
    }

    @Override
    public void guardar(OrdenRoot orden) {
        try {
            sagas.save(entidadSagaDe(orden.saga()));
            ordenes.save(entidadOrdenDe(orden));
            ordenes.flush(); // fuerza el chequeo de version aquí, no en el commit de fuera
        } catch (OptimisticLockingFailureException e) {
            throw new ConcurrenciaOptimistaException(orden.sagaId(), orden.version());
        }
    }

    @Override
    public List<CandidataOrden> buscarEjecutables(Instant ahora, int limite) {
        return ordenes.buscarCandidatas(ahora, limite).stream()
                .map(fila -> new CandidataOrden(SagaId.de(fila.getSagaId()), new TipoOrden(fila.getTipo())))
                .toList();
    }

    @Override
    public boolean hayEjecutables(Instant ahora) {
        return ordenes.existeCandidata(ahora) > 0;
    }

    @Override
    public long purgarFinalizadasAntesDe(Instant corte) {
        var ids = ordenes.idsFinalizadasAntesDe(corte);
        if (ids.isEmpty()) {
            return 0;
        }
        ordenes.borrarPorIds(ids);
        sagas.borrarPorIds(ids);
        return ids.size();
    }

    // ------------------------------------------------------------------
    // OrdenEntity <-> OrdenRoot
    // ------------------------------------------------------------------

    private static OrdenEntity entidadOrdenDe(OrdenRoot orden) {
        return new OrdenEntity(orden.sagaId().valor().toString(), orden.intentos(), orden.proximoReintentoEn(),
                orden.tokenTrabajador() == null ? null : orden.tokenTrabajador().toString(),
                orden.tokenExpiraEn(), orden.ticketAbiertoEn(),
                orden.resultado() == null ? null : orden.resultado().name(), orden.version());
    }

    private static UUID uuidONull(String valor) {
        return valor == null ? null : UUID.fromString(valor);
    }

    private static ResultadoOrden resultadoONull(String valor) {
        return valor == null ? null : ResultadoOrden.valueOf(valor);
    }

    // ------------------------------------------------------------------
    // SagaEntity <-> Saga (despacho por tipo a través de MapeadorProceso)
    // ------------------------------------------------------------------

    private SagaEntity entidadSagaDe(Saga<?> saga) {
        var auditoria = new ArrayList<AuditoriaEntity>();
        for (var a : saga.auditoria()) {
            auditoria.add(new AuditoriaEntity(a.cuando(), a.quien().usuario(), a.accion(), a.detalle()));
        }
        var sagaId = saga.id().valor().toString();
        var externalId = saga.externalId().valor().toString();
        var persistible = mapeadorDe(saga.tipo()).desarmar(saga);
        return new SagaEntity(sagaId, saga.tipo().valor(), externalId, persistible.estado(),
                ContextoCodec.escribir(persistible.contexto()), auditoria);
    }

    private Saga<?> sagaDesde(SagaEntity entity) {
        var id = SagaId.de(entity.getSagaId());
        var externalId = ExternalId.de(entity.getExternalId());
        var auditoria = entity.getAuditoria().stream()
                .map(a -> new AuditoriaIntervencion(a.getCuando(), new UsuarioSoporte(a.getQuien()),
                        a.getAccion(), a.getDetalle()))
                .toList();
        var contexto = ContextoCodec.leer(entity.getContexto());
        var tipo = new TipoOrden(entity.getTipo());
        return mapeadorDe(tipo).rearmar(id, externalId, entity.getEstado(), contexto, auditoria);
    }

    private MapeadorProceso mapeadorDe(TipoOrden tipo) {
        var mapeador = mapeadores.get(tipo);
        if (mapeador == null) {
            throw new IllegalStateException("No hay MapeadorProceso registrado para el tipo " + tipo);
        }
        return mapeador;
    }
}
