package com.ejemplo.app.testsoporte;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.dominio.ConcurrenciaOptimistaException;
import com.ejemplo.app.business.ordermanager.dominio.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.Proceso;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.ProcesoFalso;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.SagaPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.SagaSecundaria1;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.SagaSecundaria2;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.SagaSecundaria3;

/**
 * Fake de RepositorioOrden compartido por los tests de ordermanager (con
 * {@link ProcesoFalso}) y de sagas (con los procesos concretos): vive fuera de
 * ambos paquetes porque, a diferencia de la implementación real (que despacha
 * por tipo a través de {@code MapeadorProceso}), este doble necesita conocer
 * las clases concretas para poder copiarlas. Se comporta como una BD real a
 * efectos de los escenarios de la Fase 4 -- cada `cargar`/`guardar` maneja una
 * copia independiente del agregado (no la misma referencia mutable) y
 * `guardar` comprueba la version para reproducir el optimistic locking de
 * Hibernate.
 */
public final class RepositorioOrdenEnMemoria implements RepositorioOrden {

    private final Map<OrdenId, OrdenRoot> almacen = new LinkedHashMap<>();

    @Override
    public void crear(OrdenRoot orden) {
        almacen.put(orden.id(), copiar(orden));
    }

    @Override
    public OrdenRoot cargar(OrdenId id) {
        var orden = almacen.get(id);
        if (orden == null) {
            throw new IllegalArgumentException("No existe la orden " + id.valor());
        }
        return copiar(orden);
    }

    @Override
    public OrdenRoot guardar(OrdenRoot orden) {
        var actual = almacen.get(orden.id());
        if (actual == null || actual.version() != orden.version()) {
            throw new ConcurrenciaOptimistaException(orden.id(), orden.version());
        }
        var guardada = incrementarVersion(orden);
        almacen.put(orden.id(), guardada);
        return guardada;
    }

    @Override
    public List<CandidataOrden> buscarEjecutables(Instant ahora, int limite) {
        return almacen.values().stream()
                .filter(OrdenRoot::estaViva)
                .filter(o -> !o.proximoReintentoEn().isAfter(ahora))
                .filter(o -> !o.tieneTokenVigente(ahora))
                .sorted((a, b) -> a.proximoReintentoEn().compareTo(b.proximoReintentoEn()))
                .limit(limite)
                .map(o -> new CandidataOrden(o.id(), o.tipo()))
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
                .map(OrdenRoot::id)
                .toList();
        ids.forEach(almacen::remove);
        return ids.size();
    }

    /** Solo para inspección desde el test: estado actual sin pasar por un caso de uso. */
    public OrdenRoot estadoActual(OrdenId id) {
        return copiar(almacen.get(id));
    }

    /** Solo para inspección desde el test: todas las órdenes almacenadas (p. ej. tras crear hijas). */
    public List<OrdenRoot> todas() {
        return almacen.values().stream().map(RepositorioOrdenEnMemoria::copiar).toList();
    }

    private static OrdenRoot incrementarVersion(OrdenRoot orden) {
        return OrdenRoot.rehidratar(copiarProceso(orden.proceso()), orden.intentos(), orden.proximoReintentoEn(),
                orden.tokenTrabajador(), orden.tokenExpiraEn(), orden.ticketAbiertoEn(), orden.completadaEn(),
                orden.ultimoError(), orden.version() + 1);
    }

    private static OrdenRoot copiar(OrdenRoot orden) {
        return OrdenRoot.rehidratar(copiarProceso(orden.proceso()), orden.intentos(), orden.proximoReintentoEn(),
                orden.tokenTrabajador(), orden.tokenExpiraEn(), orden.ticketAbiertoEn(), orden.completadaEn(),
                orden.ultimoError(), orden.version());
    }

    private static Proceso<?> copiarProceso(Proceso<?> proceso) {
        return switch (proceso) {
            case ProcesoFalso p -> ProcesoFalso.rehidratar(
                    p.id(), p.externalId(), p.estado(), new ArrayList<>(p.auditoria()));
            case SagaPrincipal s -> SagaPrincipal.rehidratar(
                    s.id(), s.externalId(), s.contexto(), s.estado(), new ArrayList<>(s.auditoria()));
            case SagaSecundaria1 s -> SagaSecundaria1.rehidratar(
                    s.id(), s.externalId(), s.refPaso1(), s.refInicio(), s.refConfirmacion(),
                    s.estado(), new ArrayList<>(s.auditoria()));
            case SagaSecundaria2 s -> SagaSecundaria2.rehidratar(
                    s.id(), s.externalId(), s.refPaso5(), s.refRespuesta(), s.estado(), new ArrayList<>(s.auditoria()));
            case SagaSecundaria3 s -> SagaSecundaria3.rehidratar(
                    s.id(), s.externalId(), s.refPaso7(), s.refEjecucion(), s.estado(), new ArrayList<>(s.auditoria()));
            default -> throw new IllegalStateException("Tipo de proceso desconocido: " + proceso.getClass());
        };
    }
}
