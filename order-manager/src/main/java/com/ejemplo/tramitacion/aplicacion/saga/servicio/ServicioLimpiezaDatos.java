package com.ejemplo.tramitacion.aplicacion.saga.servicio;

import java.time.Instant;

import com.ejemplo.tramitacion.aplicacion.saga.puerto.entrada.CasoUsoLimpiarDatosAntiguos;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.salida.PuertoColaTareas;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.salida.PuertoMensajesProcesados;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.salida.RepositorioSagaPrincipal;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.salida.RepositorioSagasSucesoras;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.salida.UnidadDeTrabajo;

/**
 * Limpieza de datos antiguos. Todo va en UNA transacción: o se purga el corte
 * completo (sagas + dedup + tareas) o no se purga nada, así una caída a mitad
 * no deja tareas huérfanas de su saga.
 *
 * Solo borra lo que acabó bien y ya es viejo; el criterio vive en los puertos:
 * sagas COMPLETADA/CANCELADA, dedup caducado y tareas COMPLETADAs. Las órdenes
 * FALLIDAs y cualquier saga con trabajo pendiente sobreviven a la limpieza.
 */
public class ServicioLimpiezaDatos implements CasoUsoLimpiarDatosAntiguos {

    private final RepositorioSagaPrincipal repoPrincipal;
    private final RepositorioSagasSucesoras repoSucesoras;
    private final PuertoMensajesProcesados dedup;
    private final PuertoColaTareas cola;
    private final UnidadDeTrabajo tx;

    public ServicioLimpiezaDatos(RepositorioSagaPrincipal repoPrincipal,
            RepositorioSagasSucesoras repoSucesoras, PuertoMensajesProcesados dedup,
            PuertoColaTareas cola, UnidadDeTrabajo tx) {
        this.repoPrincipal = repoPrincipal;
        this.repoSucesoras = repoSucesoras;
        this.dedup = dedup;
        this.cola = cola;
        this.tx = tx;
    }

    @Override
    public ResultadoLimpieza purgarAnterioresA(Instant corte) {
        return tx.enTransaccion(() -> new ResultadoLimpieza(
                repoPrincipal.purgarFinalizadasAntesDe(corte),
                repoSucesoras.purgarFinalizadasAntesDe(corte),
                dedup.purgarAnterioresA(corte),
                cola.purgarTerminadasAntesDe(corte)));
    }
}
