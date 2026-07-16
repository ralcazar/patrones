package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import java.time.Instant;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoLimpiarDatosAntiguos;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoMensajesProcesados;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.UnidadDeTrabajo;

/**
 * Limpieza de datos antiguos. Todo va en UNA transacción: o se purga el corte
 * completo (órdenes + dedup) o no se purga nada.
 *
 * Solo borra órdenes finalizadas ({@code resultado} no nulo) y ya viejas;
 * cualquier orden viva sobrevive a la limpieza, sea cual sea su estado.
 */
@Service
public class ServicioLimpiezaDatos implements CasoUsoLimpiarDatosAntiguos {

    private final RepositorioOrden repo;
    private final PuertoMensajesProcesados dedup;
    private final UnidadDeTrabajo tx;

    public ServicioLimpiezaDatos(RepositorioOrden repo, PuertoMensajesProcesados dedup, UnidadDeTrabajo tx) {
        this.repo = repo;
        this.dedup = dedup;
        this.tx = tx;
    }

    @Override
    public ResultadoLimpieza purgarAnterioresA(Instant corte) {
        return tx.enTransaccion(() -> new ResultadoLimpieza(
                repo.purgarFinalizadasAntesDe(corte),
                dedup.purgarAnterioresA(corte)));
    }
}
