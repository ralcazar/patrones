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
import com.ejemplo.app.business.ordermanager.dominio.DetalleError;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.Proceso;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.TipoOrden;
import com.ejemplo.app.business.ordermanager.dominio.UsuarioSoporte;

/**
 * ÚNICO adaptador de escritura del agregado: OrdenRoot (que contiene su
 * Proceso), en UNA ÚNICA fila (tabla {@code orden}, negocio + ejecución
 * fusionados). Mapea dominio&lt;-&gt;entidad JPA despachando la subclase de
 * Proceso por {@code tipo} a través de la SPI {@link MapeadorProceso} (una
 * implementación por tipo, indexadas por {@link MapeadorProceso#tipo()}), y
 * traduce el conflicto de versión de Hibernate a
 * {@link ConcurrenciaOptimistaException}.
 */
@Component
public class AdaptadorRepositorioOrden implements RepositorioOrden {

    private final OrdenJpaRepository ordenes;
    private final Map<TipoOrden, MapeadorProceso> mapeadores;

    public AdaptadorRepositorioOrden(OrdenJpaRepository ordenes, List<MapeadorProceso> mapeadores) {
        this.ordenes = ordenes;
        this.mapeadores = mapeadores.stream()
                .collect(Collectors.toUnmodifiableMap(MapeadorProceso::tipo, m -> m));
    }

    @Override
    public void crear(OrdenRoot orden) {
        // La fila orden es el padre de la relación (la satélite específica del tipo
        // tiene FK a orden), así que debe existir antes de que se pueda insertar la
        // satélite. marcarComoNueva() fuerza persist() en vez de merge() (el id lo
        // asigna el dominio, no @GeneratedValue): sin esto, Hibernate haría un INSERT +
        // un UPDATE extra al procesar la colección auditoria, bumpeando version de 0 a 1
        // en el alta.
        var entity = entidadOrdenDe(orden);
        entity.marcarComoNueva();
        ordenes.save(entity);
        mapeadorDe(orden.proceso().tipo()).guardarContexto(orden.proceso());
    }

    @Override
    public OrdenRoot cargar(OrdenId id) {
        var ordenId = id.valor();
        // UN ÚNICO findById sobre la fila orden: negocio (estado del Proceso) y
        // ejecución (intentos, token, lease) comparten fila y version, así que esta
        // única consulta ya es una foto atómica de ambos. Si se leyeran en dos SELECT
        // separados (uno por negocio, otro por ejecución) un commit ajeno podría colarse
        // entre medias y devolver una combinación que nunca existió junta (torn read).
        // El satélite específico del tipo (ver MapeadorProceso) se lee DESPUÉS, fuera de
        // esta foto atómica: no hace falta que esté dentro porque cualquier commit ajeno
        // que lo module tiene que pasar por guardar() (que hace su propio
        // findById+save+flush sobre esta MISMA fila orden) y por tanto bumpea su version;
        // el guardado posterior de ESTA carga usará la version ya obsoleta que tenemos
        // aquí y el candado optimista de JPA lo detectará al hacer flush.
        var ordenEntity = ordenes.findById(ordenId)
                .orElseThrow(() -> new IllegalArgumentException("No existe la orden " + ordenId));
        return OrdenRoot.rehidratar(procesoDesde(ordenEntity), ordenEntity.getIntentos(),
                ordenEntity.getProximoReintentoEn(), uuidONull(ordenEntity.getTokenTrabajador()),
                ordenEntity.getTokenExpiraEn(), ordenEntity.getTicketAbiertoEn(),
                ordenEntity.getCompletadaEn(), detalleErrorDesde(ordenEntity), ordenEntity.getVersion());
    }

    @Override
    public OrdenRoot guardar(OrdenRoot orden) {
        try {
            var ordenEntity = ordenes.save(entidadOrdenDe(orden));
            mapeadorDe(orden.proceso().tipo()).guardarContexto(orden.proceso());
            ordenes.flush(); // fuerza el chequeo de version aquí, no en el commit de fuera; ya deja la version real en ordenEntity
            return OrdenRoot.rehidratar(orden.proceso(), ordenEntity.getIntentos(), ordenEntity.getProximoReintentoEn(),
                    orden.tokenTrabajador(), ordenEntity.getTokenExpiraEn(), ordenEntity.getTicketAbiertoEn(),
                    ordenEntity.getCompletadaEn(), detalleErrorDesde(ordenEntity), ordenEntity.getVersion());
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
        // explícito, en la misma transacción, hijas antes que padre: auditoría ->
        // satélites -> orden (todas las FK son orden_id -> orden).
        ordenes.borrarAuditoriaPorIds(ids); // proceso_auditoria es hija de orden por FK
        // Cada mapeador borra en SU propia satélite; las filas de otros tipos no matchean.
        for (var mapeador : mapeadores.values()) {
            mapeador.borrarContexto(ids);
        }
        ordenes.borrarPorIds(ids); // el padre, ahora libre de FKs de sus hijas
        return ids.size();
    }

    @Override
    public List<ExternalId> externalIdsFinalizadosAntesDe(Instant corte) {
        return ordenes.externalIdsFinalizadosAntesDe(corte).stream()
                .map(ExternalId::de)
                .toList();
    }

    @Override
    public long purgarPorExternalIds(List<ExternalId> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        var externalIdValores = ids.stream().map(id -> id.valor().toString()).toList();
        var ordenIds = ordenes.idsPorExternalIds(externalIdValores);
        if (ordenIds.isEmpty()) {
            return 0;
        }
        // Mismo patrón que purgarFinalizadasAntesDe: sin ON DELETE CASCADE (prohibido, ver
        // CLAUDE.md), hijas antes que padre, en la misma transacción: auditoría -> satélites -> orden.
        ordenes.borrarAuditoriaPorIds(ordenIds);
        for (var mapeador : mapeadores.values()) {
            mapeador.borrarContexto(ordenIds);
        }
        ordenes.borrarPorIds(ordenIds);
        return ordenIds.size();
    }

    // ------------------------------------------------------------------
    // OrdenEntity <-> OrdenRoot (despacho por tipo a través de MapeadorProceso)
    // ------------------------------------------------------------------

    private OrdenEntity entidadOrdenDe(OrdenRoot orden) {
        var proceso = orden.proceso();
        var auditoria = new ArrayList<AuditoriaEntity>();
        for (var a : proceso.auditoria()) {
            auditoria.add(new AuditoriaEntity(a.cuando(), a.quien().usuario(), a.accion(), a.detalle()));
        }
        var externalId = proceso.externalId().valor().toString();
        var estado = mapeadorDe(proceso.tipo()).estado(proceso);
        var error = orden.ultimoError();
        return new OrdenEntity(orden.id().valor(), proceso.tipo().valor(), externalId, estado, auditoria,
                orden.intentos(), orden.proximoReintentoEn(),
                orden.tokenTrabajador() == null ? null : orden.tokenTrabajador().toString(),
                orden.tokenExpiraEn(), orden.ticketAbiertoEn(), orden.completadaEn(),
                error == null ? null : error.tipo(), error == null ? null : error.mensaje(),
                orden.version());
    }

    private static DetalleError detalleErrorDesde(OrdenEntity entity) {
        return entity.getUltimoErrorTipo() == null ? null
                : new DetalleError(entity.getUltimoErrorTipo(), entity.getUltimoErrorMensaje());
    }

    private static UUID uuidONull(String valor) {
        return valor == null ? null : UUID.fromString(valor);
    }

    private Proceso<?> procesoDesde(OrdenEntity entity) {
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
