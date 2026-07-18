package com.ejemplo.app.business.sagas.aplicacion.servicio.comun;

import jakarta.transaction.Transactional;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.sagas.aplicacion.puerto.entrada.CasoUsoPurgarDatosNegocioHuerfanos;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.RepositorioDatosNegocio;

/**
 * Purga de {@code datos_negocio} huérfanos. Todo va en UNA transacción: o se
 * purgan todos los huérfanos del corte o no se purga ninguno.
 *
 * Invariante: un datos_negocio solo se considera huérfano cuando NINGÚN
 * proceso (de las 4 sagas de la tramitación) comparte ya su externalId; eso
 * solo ocurre tras la limpieza del motor (ServicioLimpiezaDatos) purgar la
 * tramitación completa, así que nunca se borra un datos_negocio en uso.
 */
@Service
public class ServicioPurgarDatosNegocioHuerfanos implements CasoUsoPurgarDatosNegocioHuerfanos {

    private final RepositorioDatosNegocio repoDatos;

    public ServicioPurgarDatosNegocioHuerfanos(RepositorioDatosNegocio repoDatos) {
        this.repoDatos = repoDatos;
    }

    @Override
    @Transactional
    public long purgarHuerfanos() {
        var huerfanos = repoDatos.idsHuerfanos();
        huerfanos.forEach(repoDatos::borrar);
        return huerfanos.size();
    }
}
