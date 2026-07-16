package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoContinuarSaga;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.UnidadDeTrabajo;
import com.ejemplo.app.business.ordermanager.dominio.comun.ConcurrenciaOptimistaException;
import com.ejemplo.app.business.ordermanager.dominio.comun.PoliticaReintentos;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;

/**
 * Bucle de ejecución de una saga: reclama el token bajo optimistic lock y
 * encadena pasos síncronos hasta que el orquestador aparca, termina o falla.
 * Cada paso persiste por su cuenta (ver {@link OrquestadorSaga}); este bucle
 * solo decide cuándo seguir, aparcar, reintentar o rendirse ante otro pod.
 */
@Service
public class ServicioContinuarSaga implements CasoUsoContinuarSaga {

    private final Map<TipoSaga, OrquestadorSaga> orquestadores;
    private final RepositorioOrden repo;
    private final UnidadDeTrabajo tx;
    private final PoliticaReintentos politica;
    private final Duration lease;

    public ServicioContinuarSaga(Map<TipoSaga, OrquestadorSaga> orquestadores, RepositorioOrden repo,
            UnidadDeTrabajo tx, PoliticaReintentos politica, Duration lease) {
        this.orquestadores = orquestadores;
        this.repo = repo;
        this.tx = tx;
        this.politica = politica;
        this.lease = lease;
    }

    @Override
    public void continuar(SagaId id, TipoSaga tipo) {
        if (!reclamarToken(id)) {
            return;
        }
        var orquestador = orquestadores.get(tipo);
        while (true) {
            SenalPaso senal;
            try {
                senal = orquestador.ejecutarPaso(id);
            } catch (ConcurrenciaOptimistaException e) {
                return; // otro pod/actor tocó el agregado entre medias
            } catch (RuntimeException e) {
                programarReintento(id);
                return;
            }
            switch (senal) {
                case SenalPaso.HayMasTrabajo() -> { /* el orquestador ya reseteó intentos y renovó el lease */ }
                case SenalPaso.Aparcar(var ventana) -> { return; } // ya persistido por el orquestador
                case SenalPaso.Finalizada(var resultado) -> { return; } // ya persistido por el orquestador
            }
        }
    }

    /** Reclamo con optimistic lock: si otro pod ya la tomó (o venció el conflicto), nos retiramos. */
    private boolean reclamarToken(SagaId id) {
        try {
            return tx.enTransaccion(() -> {
                var orden = repo.cargar(id);
                var ahora = Instant.now();
                if (!orden.estaViva() || orden.tieneTokenVigente(ahora)) {
                    return false;
                }
                orden.asignarToken(UUID.randomUUID(), lease, ahora);
                repo.guardar(orden);
                return true;
            });
        } catch (ConcurrenciaOptimistaException e) {
            return false;
        }
    }

    @Override
    public void continuarCandidatas(int limite) {
        for (var candidata : repo.buscarEjecutables(Instant.now(), limite)) {
            continuar(candidata.sagaId(), candidata.tipo());
        }
    }

    private void programarReintento(SagaId id) {
        tx.enTransaccion(() -> {
            var orden = repo.cargar(id);
            orden.programarReintento(politica, Instant.now());
            repo.guardar(orden);
        });
    }
}
