package com.ejemplo.tramitacion.infraestructura.orden;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ejemplo.tramitacion.dominio.orden.EstadoOrden;
import com.ejemplo.tramitacion.dominio.orden.Orden;

/**
 * Frontera transaccional del GestorOrdenes, más el método {@link #encolar}:
 * la pieza que une las sagas con la cola.
 */
@Service
public class ServicioOrdenes {

    private static final Logger log = LoggerFactory.getLogger(ServicioOrdenes.class);

    private final RepositorioOrdenes repo;
    private final Duration lease;
    private final int ventanaReclamo;

    public ServicioOrdenes(RepositorioOrdenes repo,
                           @Value("${gestor-ordenes.lease-segundos:300}") long leaseSegundos,
                           @Value("${gestor-ordenes.ventana-reclamo:20}") int ventanaReclamo) {
        this.repo = repo;
        this.lease = Duration.ofSeconds(leaseSegundos);
        this.ventanaReclamo = ventanaReclamo;
    }

    /**
     * Inserta una orden-tarea. REQUIRED: si el llamante (los servicios de saga
     * vía UnidadDeTrabajo) tiene transacción abierta, se une a ella -> el
     * estado de la saga y su tarea de continuación se commitean JUNTOS. Este
     * es el invariante que hace innecesaria la recuperación aparte.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void encolar(String contenido, String sagaId, String tipoTarea, Instant ejecutarDesde) {
        repo.save(Orden.tareaSaga(contenido, sagaId, tipoTarea, ejecutarDesde));
    }

    /**
     * Borra las órdenes COMPLETADAs anteriores al corte. REQUIRED: se une a la
     * transacción de la limpieza de sagas para que todo commitee junto.
     * Las FALLIDAs no se tocan: significan bug o infraestructura rota.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public long purgarCompletadas(Instant corte) {
        return repo.purgarCompletadasAntesDe(corte);
    }

    @Transactional(readOnly = true)
    public boolean hayTrabajoPendiente() {
        Instant ahora = Instant.now();
        return !repo.buscarCandidatas(ahora, ahora.minus(lease), 1).isEmpty();
    }

    @Transactional
    public Optional<Orden> reclamarSiguiente(String token) {
        Instant ahora = Instant.now();
        Instant limiteLease = ahora.minus(lease);

        List<Long> candidatas = repo.buscarCandidatas(ahora, limiteLease, ventanaReclamo);
        if (candidatas.isEmpty()) {
            return Optional.empty();
        }
        Collections.shuffle(candidatas);

        for (Long id : candidatas) {
            if (repo.reclamar(id, token, ahora, limiteLease) == 1) {
                return repo.findById(id);
            }
        }
        return Optional.empty();
    }

    @Transactional
    public void finalizar(Long id, String token, boolean ok) {
        EstadoOrden resultado = ok ? EstadoOrden.COMPLETADA : EstadoOrden.FALLIDA;
        int actualizadas = repo.finalizar(id, resultado.name(), token);
        if (actualizadas == 0) {
            log.warn("Lease perdido en orden {} (token {}); resultado no escrito, "
                    + "otro trabajador la posee ahora", id, token);
        }
    }
}
