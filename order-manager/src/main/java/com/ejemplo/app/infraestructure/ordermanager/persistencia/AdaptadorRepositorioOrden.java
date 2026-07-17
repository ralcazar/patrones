package com.ejemplo.app.infraestructure.ordermanager.persistencia;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.dominio.comun.AuditoriaIntervencion;
import com.ejemplo.app.business.ordermanager.dominio.comun.ConcurrenciaOptimistaException;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.comun.OrdenRoot;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso1;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso5;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso7;
import com.ejemplo.app.business.ordermanager.dominio.comun.ResultadoOrden;
import com.ejemplo.app.business.ordermanager.dominio.comun.Saga;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;
import com.ejemplo.app.business.ordermanager.dominio.comun.UsuarioSoporte;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ContextoTramitacion;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.DatoNegocio2;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.DatoNegocio3;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.EstadoSagaPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.RefPaso2;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.RefPaso3;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.RefPaso4;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.RefPaso6;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.RefPaso8;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.SagaPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.EstadoSagaSecundaria1;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.RefConfirmacion;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.RefInicio;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.SagaSecundaria1;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.EstadoSagaSecundaria2;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.RefRespuesta;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.SagaSecundaria2;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.EstadoSagaSecundaria3;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.RefEjecucion;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.SagaSecundaria3;

/**
 * ÚNICO adaptador de escritura del agregado: OrdenRoot (que contiene su
 * Saga). Mapea dominio&lt;-&gt;entidades JPA despachando la subclase de
 * Saga por {@code tipo}, y traduce el conflicto de versión de Hibernate a
 * {@link ConcurrenciaOptimistaException}.
 */
@Component
public class AdaptadorRepositorioOrden implements RepositorioOrden {

    private final OrdenJpaRepository ordenes;
    private final SagaJpaRepository sagas;

    public AdaptadorRepositorioOrden(OrdenJpaRepository ordenes, SagaJpaRepository sagas) {
        this.ordenes = ordenes;
        this.sagas = sagas;
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
                .map(fila -> new CandidataOrden(SagaId.de(fila.getSagaId()), TipoSaga.valueOf(fila.getTipo())))
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
    // SagaEntity <-> Saga (despacho por tipo)
    // ------------------------------------------------------------------

    private static SagaEntity entidadSagaDe(Saga<?> saga) {
        var auditoria = new ArrayList<AuditoriaEntity>();
        for (var a : saga.auditoria()) {
            auditoria.add(new AuditoriaEntity(a.cuando(), a.quien().usuario(), a.accion(), a.detalle()));
        }
        var sagaId = saga.id().valor().toString();
        var externalId = saga.externalId().valor().toString();
        return switch (saga) {
            case SagaPrincipal s -> new SagaEntity(sagaId, TipoSaga.PRINCIPAL.name(), externalId,
                    s.estado().name(), ContextoCodec.escribir(contextoDePrincipal(s.contexto())), auditoria);
            case SagaSecundaria1 s -> new SagaEntity(sagaId, TipoSaga.SECUNDARIA1.name(), externalId,
                    s.estado().name(), ContextoCodec.escribir(contextoDeSecundaria1(s)), auditoria);
            case SagaSecundaria2 s -> new SagaEntity(sagaId, TipoSaga.SECUNDARIA2.name(), externalId,
                    s.estado().name(), ContextoCodec.escribir(contextoDeSecundaria2(s)), auditoria);
            case SagaSecundaria3 s -> new SagaEntity(sagaId, TipoSaga.SECUNDARIA3.name(), externalId,
                    s.estado().name(), ContextoCodec.escribir(contextoDeSecundaria3(s)), auditoria);
            default -> throw new IllegalStateException("Tipo de saga desconocido: " + saga.getClass());
        };
    }

    private static Saga<?> sagaDesde(SagaEntity entity) {
        var id = SagaId.de(entity.getSagaId());
        var externalId = ExternalId.de(entity.getExternalId());
        var auditoria = entity.getAuditoria().stream()
                .map(a -> new AuditoriaIntervencion(a.getCuando(), new UsuarioSoporte(a.getQuien()),
                        a.getAccion(), a.getDetalle()))
                .toList();
        var ctx = ContextoCodec.leer(entity.getContexto());
        return switch (TipoSaga.valueOf(entity.getTipo())) {
            case PRINCIPAL -> SagaPrincipal.rehidratar(id, externalId, contextoTramitacionDesde(ctx),
                    EstadoSagaPrincipal.valueOf(entity.getEstado()), auditoria);
            case SECUNDARIA1 -> SagaSecundaria1.rehidratar(id, externalId, new RefPaso1(ctx.get("refPaso1")),
                    refONull(ctx, "refInicio", RefInicio::new), refONull(ctx, "refConfirmacion", RefConfirmacion::new),
                    EstadoSagaSecundaria1.valueOf(entity.getEstado()), auditoria);
            case SECUNDARIA2 -> SagaSecundaria2.rehidratar(id, externalId, new RefPaso5(ctx.get("refPaso5")),
                    refONull(ctx, "refRespuesta", RefRespuesta::new),
                    EstadoSagaSecundaria2.valueOf(entity.getEstado()), auditoria);
            case SECUNDARIA3 -> SagaSecundaria3.rehidratar(id, externalId, new RefPaso7(ctx.get("refPaso7")),
                    refONull(ctx, "refEjecucion", RefEjecucion::new),
                    EstadoSagaSecundaria3.valueOf(entity.getEstado()), auditoria);
        };
    }

    private static Map<String, String> contextoDePrincipal(ContextoTramitacion ctx) {
        var m = new LinkedHashMap<String, String>();
        m.put("datoNegocio3Valor1", ctx.datoNegocio3().valor1());
        m.put("datoNegocio3Valor2", ctx.datoNegocio3().valor2());
        m.put("datoNegocio2Valor1", ctx.datoNegocio2().valor1());
        m.put("datoNegocio2Valor2", ctx.datoNegocio2().valor2());
        ponerSiNoNulo(m, "refPaso1", ctx.refPaso1() == null ? null : ctx.refPaso1().valor());
        ponerSiNoNulo(m, "refPaso2", ctx.refPaso2() == null ? null : ctx.refPaso2().valor());
        ponerSiNoNulo(m, "refPaso3", ctx.refPaso3() == null ? null : ctx.refPaso3().valor());
        ponerSiNoNulo(m, "refPaso4", ctx.refPaso4() == null ? null : ctx.refPaso4().valor());
        ponerSiNoNulo(m, "refPaso5", ctx.refPaso5() == null ? null : ctx.refPaso5().valor());
        ponerSiNoNulo(m, "refPaso6", ctx.refPaso6() == null ? null : ctx.refPaso6().valor());
        ponerSiNoNulo(m, "refPaso7", ctx.refPaso7() == null ? null : ctx.refPaso7().valor());
        ponerSiNoNulo(m, "refPaso8", ctx.refPaso8() == null ? null : ctx.refPaso8().valor());
        return m;
    }

    private static ContextoTramitacion contextoTramitacionDesde(Map<String, String> ctx) {
        return ContextoTramitacion.rehidratar(
                new DatoNegocio3(ctx.get("datoNegocio3Valor1"), ctx.get("datoNegocio3Valor2")),
                new DatoNegocio2(ctx.get("datoNegocio2Valor1"), ctx.get("datoNegocio2Valor2")),
                refONull(ctx, "refPaso1", RefPaso1::new), refONull(ctx, "refPaso2", RefPaso2::new),
                refONull(ctx, "refPaso3", RefPaso3::new), refONull(ctx, "refPaso4", RefPaso4::new),
                refONull(ctx, "refPaso5", RefPaso5::new), refONull(ctx, "refPaso6", RefPaso6::new),
                refONull(ctx, "refPaso7", RefPaso7::new), refONull(ctx, "refPaso8", RefPaso8::new));
    }

    private static Map<String, String> contextoDeSecundaria1(SagaSecundaria1 s) {
        var m = new LinkedHashMap<String, String>();
        m.put("refPaso1", s.refPaso1().valor());
        ponerSiNoNulo(m, "refInicio", s.refInicio() == null ? null : s.refInicio().valor());
        ponerSiNoNulo(m, "refConfirmacion", s.refConfirmacion() == null ? null : s.refConfirmacion().valor());
        return m;
    }

    private static Map<String, String> contextoDeSecundaria2(SagaSecundaria2 s) {
        var m = new LinkedHashMap<String, String>();
        m.put("refPaso5", s.refPaso5().valor());
        ponerSiNoNulo(m, "refRespuesta", s.refRespuesta() == null ? null : s.refRespuesta().valor());
        return m;
    }

    private static Map<String, String> contextoDeSecundaria3(SagaSecundaria3 s) {
        var m = new LinkedHashMap<String, String>();
        m.put("refPaso7", s.refPaso7().valor());
        ponerSiNoNulo(m, "refEjecucion", s.refEjecucion() == null ? null : s.refEjecucion().valor());
        return m;
    }

    private static void ponerSiNoNulo(Map<String, String> m, String clave, String valor) {
        if (valor != null) {
            m.put(clave, valor);
        }
    }

    private static <R> R refONull(Map<String, String> ctx, String clave, Function<String, R> fabrica) {
        var valor = ctx.get(clave);
        return valor == null ? null : fabrica.apply(valor);
    }
}
