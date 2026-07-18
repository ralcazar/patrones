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
import com.ejemplo.app.business.ordermanager.dominio.AuditoriaIntervencion;
import com.ejemplo.app.business.ordermanager.dominio.ConcurrenciaOptimistaException;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.Proceso;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.TipoOrden;
import com.ejemplo.app.business.ordermanager.dominio.UsuarioSoporte;

/**
 * ÚNICO adaptador de escritura del agregado: OrdenRoot (que contiene su
 * Proceso). Mapea dominio&lt;-&gt;entidades JPA despachando la subclase de
 * Proceso por {@code tipo} a través de la SPI {@link MapeadorProceso} (una
 * implementación por tipo, indexadas por {@link MapeadorProceso#tipo()}), y
 * traduce el conflicto de versión de Hibernate a
 * {@link ConcurrenciaOptimistaException}.
 */
@Component
public class AdaptadorRepositorioOrden implements RepositorioOrden {

    private final OrdenJpaRepository ordenes;
    private final ProcesoJpaRepository procesos;
    private final Map<TipoOrden, MapeadorProceso> mapeadores;

    public AdaptadorRepositorioOrden(OrdenJpaRepository ordenes, ProcesoJpaRepository procesos,
            List<MapeadorProceso> mapeadores) {
        this.ordenes = ordenes;
        this.procesos = procesos;
        this.mapeadores = mapeadores.stream()
                .collect(Collectors.toUnmodifiableMap(MapeadorProceso::tipo, m -> m));
    }

    @Override
    public void crear(OrdenRoot orden) {
        // La fila proceso (padre) tiene que existir antes de que la satélite (hija, FK a
        // proceso) se pueda insertar.
        procesos.save(entidadProcesoDe(orden.proceso()));
        mapeadorDe(orden.proceso().tipo()).guardarContexto(orden.proceso());
        ordenes.save(entidadOrdenDe(orden));
    }

    @Override
    public OrdenRoot cargar(OrdenId id) {
        var ordenId = id.valor();
        var procesoEntity = procesos.findById(ordenId)
                .orElseThrow(() -> new IllegalArgumentException("No existe el proceso " + ordenId));
        var ordenEntity = ordenes.findById(ordenId)
                .orElseThrow(() -> new IllegalArgumentException("No existe la orden " + ordenId));
        return OrdenRoot.rehidratar(procesoDesde(procesoEntity), ordenEntity.getIntentos(),
                ordenEntity.getProximoReintentoEn(), uuidONull(ordenEntity.getTokenTrabajador()),
                ordenEntity.getTokenExpiraEn(), ordenEntity.getTicketAbiertoEn(),
                ordenEntity.getCompletadaEn(), ordenEntity.getVersion());
    }

    @Override
    public OrdenRoot guardar(OrdenRoot orden) {
        try {
            procesos.save(entidadProcesoDe(orden.proceso()));
            mapeadorDe(orden.proceso().tipo()).guardarContexto(orden.proceso());
            var ordenEntity = ordenes.save(entidadOrdenDe(orden));
            ordenes.flush(); // fuerza el chequeo de version aquí, no en el commit de fuera; ya deja la version real en ordenEntity
            return OrdenRoot.rehidratar(orden.proceso(), ordenEntity.getIntentos(), ordenEntity.getProximoReintentoEn(),
                    orden.tokenTrabajador(), ordenEntity.getTokenExpiraEn(), ordenEntity.getTicketAbiertoEn(),
                    ordenEntity.getCompletadaEn(), ordenEntity.getVersion());
        } catch (OptimisticLockingFailureException e) {
            throw new ConcurrenciaOptimistaException(orden.id(), orden.version());
        }
    }

    @Override
    public List<CandidataOrden> buscarEjecutables(Instant ahora, int limite) {
        return ordenes.buscarCandidatas(ahora, limite).stream()
                .map(fila -> new CandidataOrden(OrdenId.de(fila.getOrdenId()), new TipoOrden(fila.getTipo())))
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
        // Sin ON DELETE CASCADE (prohibido, ver CLAUDE.md): el borrado de las hijas es
        // explícito, en la misma transacción, hijas antes que padre.
        ordenes.borrarPorIds(ids); // orden es hija de proceso por FK
        procesos.borrarAuditoriaPorIds(ids); // proceso_auditoria es hija de proceso por FK
        // Cada mapeador borra en SU propia satélite; las filas de otros tipos no matchean.
        for (var mapeador : mapeadores.values()) {
            mapeador.borrarContexto(ids);
        }
        procesos.borrarPorIds(ids); // el padre, ahora libre de FKs de sus hijas
        return ids.size();
    }

    // ------------------------------------------------------------------
    // OrdenEntity <-> OrdenRoot
    // ------------------------------------------------------------------

    private static OrdenEntity entidadOrdenDe(OrdenRoot orden) {
        return new OrdenEntity(orden.id().valor(), orden.intentos(), orden.proximoReintentoEn(),
                orden.tokenTrabajador() == null ? null : orden.tokenTrabajador().toString(),
                orden.tokenExpiraEn(), orden.ticketAbiertoEn(),
                orden.completadaEn(), orden.version());
    }

    private static UUID uuidONull(String valor) {
        return valor == null ? null : UUID.fromString(valor);
    }

    // ------------------------------------------------------------------
    // ProcesoEntity <-> Proceso (despacho por tipo a través de MapeadorProceso)
    // ------------------------------------------------------------------

    private ProcesoEntity entidadProcesoDe(Proceso<?> proceso) {
        var auditoria = new ArrayList<AuditoriaEntity>();
        for (var a : proceso.auditoria()) {
            auditoria.add(new AuditoriaEntity(a.cuando(), a.quien().usuario(), a.accion(), a.detalle()));
        }
        var ordenId = proceso.id().valor();
        var externalId = proceso.externalId().valor().toString();
        var estado = mapeadorDe(proceso.tipo()).estado(proceso);
        return new ProcesoEntity(ordenId, proceso.tipo().valor(), externalId, estado, auditoria);
    }

    private Proceso<?> procesoDesde(ProcesoEntity entity) {
        var id = new OrdenId(entity.getOrdenId());
        var externalId = ExternalId.de(entity.getExternalId());
        var auditoria = entity.getAuditoria().stream()
                .map(a -> new AuditoriaIntervencion(a.getCuando(), new UsuarioSoporte(a.getQuien()),
                        a.getAccion(), a.getDetalle()))
                .toList();
        var tipo = new TipoOrden(entity.getTipo());
        return mapeadorDe(tipo).rearmar(id, externalId, entity.getEstado(), auditoria);
    }

    private MapeadorProceso mapeadorDe(TipoOrden tipo) {
        var mapeador = mapeadores.get(tipo);
        if (mapeador == null) {
            throw new IllegalStateException("No hay MapeadorProceso registrado para el tipo " + tipo);
        }
        return mapeador;
    }
}
