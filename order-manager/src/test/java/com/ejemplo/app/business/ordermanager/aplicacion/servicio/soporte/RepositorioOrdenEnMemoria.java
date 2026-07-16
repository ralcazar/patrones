package com.ejemplo.app.business.ordermanager.aplicacion.servicio.soporte;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.dominio.comun.ConcurrenciaOptimistaException;
import com.ejemplo.app.business.ordermanager.dominio.comun.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.comun.Saga;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.SagaPrincipal;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria1.SagaSecundaria1;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria2.SagaSecundaria2;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria3.SagaSecundaria3;

/**
 * Fake de RepositorioOrden para tests de la capa de aplicación: se comporta
 * como una BD real a efectos de los escenarios de la Fase 4 -- cada
 * `cargar`/`guardar` maneja una copia independiente del agregado (no la misma
 * referencia mutable) y `guardar` comprueba la version para reproducir el
 * optimistic locking de Hibernate.
 */
public final class RepositorioOrdenEnMemoria implements RepositorioOrden {

    private final Map<SagaId, OrdenRoot> almacen = new LinkedHashMap<>();

    @Override
    public void crear(OrdenRoot orden) {
        almacen.put(orden.sagaId(), copiar(orden));
    }

    @Override
    public OrdenRoot cargar(SagaId id) {
        var orden = almacen.get(id);
        if (orden == null) {
            throw new IllegalArgumentException("No existe la orden " + id.valor());
        }
        return copiar(orden);
    }

    @Override
    public void guardar(OrdenRoot orden) {
        var actual = almacen.get(orden.sagaId());
        if (actual == null || actual.version() != orden.version()) {
            throw new ConcurrenciaOptimistaException(orden.sagaId(), orden.version());
        }
        almacen.put(orden.sagaId(), incrementarVersion(orden));
    }

    @Override
    public List<CandidataOrden> buscarEjecutables(Instant ahora, int limite) {
        return almacen.values().stream()
                .filter(OrdenRoot::estaViva)
                .filter(o -> !o.proximoReintentoEn().isAfter(ahora))
                .filter(o -> !o.tieneTokenVigente(ahora))
                .sorted((a, b) -> a.proximoReintentoEn().compareTo(b.proximoReintentoEn()))
                .limit(limite)
                .map(o -> new CandidataOrden(o.sagaId(), o.tipo()))
                .toList();
    }

    @Override
    public boolean hayEjecutables(Instant ahora) {
        return almacen.values().stream()
                .filter(OrdenRoot::estaViva)
                .filter(o -> !o.proximoReintentoEn().isAfter(ahora))
                .anyMatch(o -> !o.tieneTokenVigente(ahora));
    }

    @Override
    public long purgarFinalizadasAntesDe(Instant corte) {
        var ids = almacen.values().stream()
                .filter(o -> !o.estaViva())
                .map(OrdenRoot::sagaId)
                .toList();
        ids.forEach(almacen::remove);
        return ids.size();
    }

    /** Solo para inspección desde el test: estado actual sin pasar por un caso de uso. */
    public OrdenRoot estadoActual(SagaId id) {
        return copiar(almacen.get(id));
    }

    /** Solo para inspección desde el test: todas las órdenes almacenadas (p. ej. tras crear hijas). */
    public List<OrdenRoot> todas() {
        return almacen.values().stream().map(RepositorioOrdenEnMemoria::copiar).toList();
    }

    private static OrdenRoot incrementarVersion(OrdenRoot orden) {
        return OrdenRoot.rehidratar(copiarSaga(orden.saga()), orden.intentos(), orden.proximoReintentoEn(),
                orden.tokenTrabajador(), orden.tokenExpiraEn(), orden.ticketAbiertoEn(), orden.resultado(),
                orden.version() + 1);
    }

    private static OrdenRoot copiar(OrdenRoot orden) {
        return OrdenRoot.rehidratar(copiarSaga(orden.saga()), orden.intentos(), orden.proximoReintentoEn(),
                orden.tokenTrabajador(), orden.tokenExpiraEn(), orden.ticketAbiertoEn(), orden.resultado(),
                orden.version());
    }

    private static Saga<?> copiarSaga(Saga<?> saga) {
        return switch (saga) {
            case SagaPrincipal s -> SagaPrincipal.rehidratar(
                    s.id(), s.externalId(), s.contexto(), s.estado(), new ArrayList<>(s.auditoria()));
            case SagaSecundaria1 s -> SagaSecundaria1.rehidratar(
                    s.id(), s.externalId(), s.refPaso1(), s.refInicio(), s.refConfirmacion(),
                    s.estado(), new ArrayList<>(s.auditoria()));
            case SagaSecundaria2 s -> SagaSecundaria2.rehidratar(
                    s.id(), s.externalId(), s.refPaso5(), s.refRespuesta(), s.estado(), new ArrayList<>(s.auditoria()));
            case SagaSecundaria3 s -> SagaSecundaria3.rehidratar(
                    s.id(), s.externalId(), s.refPaso7(), s.refEjecucion(), s.estado(), new ArrayList<>(s.auditoria()));
            default -> throw new IllegalStateException("Tipo de saga desconocido: " + saga.getClass());
        };
    }
}
