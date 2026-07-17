package com.ejemplo.app.business.ordermanager.aplicacion.servicio.comun;

import java.time.Instant;

import jakarta.transaction.Transactional;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoLimpiarDatosAntiguos;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoMensajesProcesados;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;

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

    public ServicioLimpiezaDatos(RepositorioOrden repo, PuertoMensajesProcesados dedup) {
        this.repo = repo;
        this.dedup = dedup;
    }

    @Override
    @Transactional
    public ResultadoLimpieza purgarAnterioresA(Instant corte) {
        return new ResultadoLimpieza(repo.purgarFinalizadasAntesDe(corte), dedup.purgarAnterioresA(corte));
    }
}
