package com.ejemplo.app.testsoporte;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.dominio.ConcurrenciaOptimistaException;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
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
        // Espejo fiel del ORDER BY de la query nativa buscarCandidatas (OrdenJpaRepository):
        // prioridad DESC, creada_en ASC, proximo_reintento_en ASC.
        return almacen.values().stream()
                .filter(OrdenRoot::estaViva)
                .filter(o -> !o.proximoReintentoEn().isAfter(ahora))
                .filter(o -> !o.tieneTokenVigente(ahora))
                .sorted(java.util.Comparator
                        .comparingInt((OrdenRoot o) -> o.prioridad().peso()).reversed()
                        .thenComparing(OrdenRoot::creadaEn)
                        .thenComparing(OrdenRoot::proximoReintentoEn))
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
    public List<ExternalId> externalIdsFinalizadosAntesDe(Instant corte) {
        // Espejo de la query nativa OrdenJpaRepository.externalIdsFinalizadosAntesDe:
        // agrupa por external_id, exige que NINGUNA orden del grupo esté viva, y que la
        // última en terminar (MAX completadaEn) sea anterior al corte.
        var porExternalId = almacen.values().stream()
                .collect(Collectors.groupingBy(o -> o.proceso().externalId()));
        return porExternalId.entrySet().stream()
                .filter(e -> e.getValue().stream().noneMatch(OrdenRoot::estaViva))
                .filter(e -> e.getValue().stream()
                        .map(OrdenRoot::completadaEn)
                        .max(Instant::compareTo)
                        .filter(maxCompletadaEn -> maxCompletadaEn.isBefore(corte))
                        .isPresent())
                .map(Map.Entry::getKey)
                .toList();
    }

    @Override
    public long purgarPorExternalIds(List<ExternalId> ids) {
        var idsBuscados = new HashSet<>(ids);
        var ordenIds = almacen.values().stream()
                .filter(o -> idsBuscados.contains(o.proceso().externalId()))
                .map(OrdenRoot::id)
                .toList();
        ordenIds.forEach(almacen::remove);
        return ordenIds.size();
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
        // rehidratar COMPLETA PRESERVANDO prioridad, creadaEn y actualizadaEn: usar una
        // sobrecarga corta resetearía la prioridad a Prioridad.normal() o las marcas
        // temporales a proximoReintentoEn, rompiendo el orden y el rastro de auditoría.
        // NO bumpea actualizadaEn aquí: ya la fijó el mutador del dominio antes de guardar.
        return OrdenRoot.rehidratar(copiarProceso(orden.proceso()), orden.prioridad(), orden.intentos(),
                orden.proximoReintentoEn(),
                orden.tokenTrabajador(), orden.tokenExpiraEn(), orden.ticketAbiertoEn(), orden.completadaEn(),
                orden.ultimoError(), orden.version() + 1, orden.creadaEn(), orden.actualizadaEn());
    }

    private static OrdenRoot copiar(OrdenRoot orden) {
        // Igual que incrementarVersion: PRESERVA prioridad, creadaEn y actualizadaEn al copiar.
        return OrdenRoot.rehidratar(copiarProceso(orden.proceso()), orden.prioridad(), orden.intentos(),
                orden.proximoReintentoEn(),
                orden.tokenTrabajador(), orden.tokenExpiraEn(), orden.ticketAbiertoEn(), orden.completadaEn(),
                orden.ultimoError(), orden.version(), orden.creadaEn(), orden.actualizadaEn());
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
