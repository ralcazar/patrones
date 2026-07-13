package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import java.time.Instant;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoLimpiarDatosAntiguos;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoColaTareas;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoMensajesProcesados;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioSagaPrincipal;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioSagaSecundaria1;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioSagaSecundaria2;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioSagaSecundaria3;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.UnidadDeTrabajo;

/**
 * Limpieza de datos antiguos. Todo va en UNA transacción: o se purga el corte
 * completo (sagas + dedup + tareas) o no se purga nada, así una caída a mitad
 * no deja tareas huérfanas de su saga.
 *
 * Solo borra lo que acabó bien y ya es viejo; el criterio vive en los puertos:
 * sagas COMPLETADA/CANCELADA, dedup caducado y tareas COMPLETADAs. Las órdenes
 * FALLIDAs y cualquier saga con trabajo pendiente sobreviven a la limpieza.
 */
@Service
public class ServicioLimpiezaDatos implements CasoUsoLimpiarDatosAntiguos {

    private final RepositorioSagaPrincipal repoPrincipal;
    private final RepositorioSagaSecundaria1 repoSecundaria1;
    private final RepositorioSagaSecundaria2 repoSecundaria2;
    private final RepositorioSagaSecundaria3 repoSecundaria3;
    private final PuertoMensajesProcesados dedup;
    private final PuertoColaTareas cola;
    private final UnidadDeTrabajo tx;

    public ServicioLimpiezaDatos(RepositorioSagaPrincipal repoPrincipal,
            RepositorioSagaSecundaria1 repoSecundaria1, RepositorioSagaSecundaria2 repoSecundaria2,
            RepositorioSagaSecundaria3 repoSecundaria3, PuertoMensajesProcesados dedup,
            PuertoColaTareas cola, UnidadDeTrabajo tx) {
        this.repoPrincipal = repoPrincipal;
        this.repoSecundaria1 = repoSecundaria1;
        this.repoSecundaria2 = repoSecundaria2;
        this.repoSecundaria3 = repoSecundaria3;
        this.dedup = dedup;
        this.cola = cola;
        this.tx = tx;
    }

    @Override
    public ResultadoLimpieza purgarAnterioresA(Instant corte) {
        return tx.enTransaccion(() -> new ResultadoLimpieza(
                repoPrincipal.purgarFinalizadasAntesDe(corte),
                repoSecundaria1.purgarFinalizadasAntesDe(corte)
                        + repoSecundaria2.purgarFinalizadasAntesDe(corte)
                        + repoSecundaria3.purgarFinalizadasAntesDe(corte),
                dedup.purgarAnterioresA(corte),
                cola.purgarTerminadasAntesDe(corte)));
    }
}
