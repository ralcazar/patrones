package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import java.time.Instant;

import jakarta.transaction.Transactional;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoLimpiarDatosAntiguos;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoMensajesProcesados;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoObservadorEjecucion;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;

/**
 * Limpieza de datos antiguos. Todo va en UNA transacción: o se purga el corte
 * completo (órdenes + dedup) o no se purga nada.
 *
 * Solo borra órdenes finalizadas ({@code completadaEn} no nula) y ya viejas;
 * cualquier orden viva sobrevive a la limpieza, sea cual sea su estado.
 */
@Service
public class ServicioLimpiezaDatos implements CasoUsoLimpiarDatosAntiguos {

    private final RepositorioOrden repo;
    private final PuertoMensajesProcesados dedup;
    private final PuertoObservadorEjecucion observador;

    public ServicioLimpiezaDatos(RepositorioOrden repo, PuertoMensajesProcesados dedup,
            PuertoObservadorEjecucion observador) {
        this.repo = repo;
        this.dedup = dedup;
        this.observador = observador;
    }

    @Override
    @Transactional
    public ResultadoLimpieza purgarAnterioresA(Instant corte) {
        var ordenesEliminadas = repo.purgarFinalizadasAntesDe(corte);
        var mensajesEliminados = dedup.purgarAnterioresA(corte);
        observador.datosAntiguosPurgados(ordenesEliminadas, mensajesEliminados);
        return new ResultadoLimpieza(ordenesEliminadas, mensajesEliminados);
    }
}
