package com.ejemplo.app.infraestructure.ordermanager;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoConciliacionSecundaria2;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoConsultaSagasSoporte;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoMensajesProcesados;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoPaso1;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoPaso2;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoPaso3;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoPaso4;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoPaso5;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoPaso6;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoPaso7;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoPaso8;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoSagaSecundaria1;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoSagaSecundaria2;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoSagaSecundaria3;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoSagasTicketPendiente;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoTicketsSoporte;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.UnidadDeTrabajo;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ServicioContinuarSaga;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ServicioIniciarTramitacion;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ServicioLimpiezaDatos;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ServicioRegistrarRespuestaSecundaria2;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ServicioSaga;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ServicioSagaPrincipal;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ServicioSagaSecundaria1;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ServicioSagaSecundaria2;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ServicioSagaSecundaria3;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ServicioSoporteSagas;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ServicioTicketsSoporte;
import com.ejemplo.app.business.ordermanager.dominio.comun.PoliticaReintentos;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;

/**
 * Wiring de los servicios de aplicación (POJOs sin anotaciones Spring: la capa
 * business no depende de ningún framework). Los adaptadores de los puertos
 * REST de paso (PuertoPasoN, PuertoSagaSecundariaN, PuertoConciliacionSecundaria2)
 * y de deduplicación (PuertoMensajesProcesados) no están implementados en este
 * esqueleto: Spring los inyectará cuando existan.
 */
@Configuration
public class ConfiguracionAplicacion {

    @Bean
    PoliticaReintentos politicaReintentos() {
        return new PoliticaReintentos();
    }

    @Bean
    ServicioSagaPrincipal servicioSagaPrincipal(RepositorioOrden repo, UnidadDeTrabajo tx,
            @Value("${orden.lease}") Duration lease,
            PuertoPaso1 p1, PuertoPaso2 p2, PuertoPaso3 p3, PuertoPaso4 p4,
            PuertoPaso5 p5, PuertoPaso6 p6, PuertoPaso7 p7, PuertoPaso8 p8) {
        return new ServicioSagaPrincipal(repo, tx, lease, p1, p2, p3, p4, p5, p6, p7, p8);
    }

    @Bean
    ServicioSagaSecundaria1 servicioSagaSecundaria1(RepositorioOrden repo, UnidadDeTrabajo tx,
            @Value("${orden.lease}") Duration lease, PuertoSagaSecundaria1 puerto) {
        return new ServicioSagaSecundaria1(repo, tx, lease, puerto);
    }

    @Bean
    ServicioSagaSecundaria2 servicioSagaSecundaria2(RepositorioOrden repo, UnidadDeTrabajo tx,
            PuertoSagaSecundaria2 puerto, PuertoConciliacionSecundaria2 conciliacion) {
        return new ServicioSagaSecundaria2(repo, tx, puerto, conciliacion);
    }

    @Bean
    ServicioSagaSecundaria3 servicioSagaSecundaria3(RepositorioOrden repo, UnidadDeTrabajo tx,
            @Value("${orden.lease}") Duration lease, PuertoSagaSecundaria3 puerto) {
        return new ServicioSagaSecundaria3(repo, tx, lease, puerto);
    }

    @Bean
    Map<TipoSaga, ServicioSaga> serviciosSagaPorTipo(ServicioSagaPrincipal principal,
            ServicioSagaSecundaria1 secundaria1, ServicioSagaSecundaria2 secundaria2,
            ServicioSagaSecundaria3 secundaria3) {
        return Map.of(
                TipoSaga.PRINCIPAL, principal,
                TipoSaga.SECUNDARIA1, secundaria1,
                TipoSaga.SECUNDARIA2, secundaria2,
                TipoSaga.SECUNDARIA3, secundaria3);
    }

    @Bean
    ServicioContinuarSaga servicioContinuarSaga(Map<TipoSaga, ServicioSaga> serviciosSaga,
            RepositorioOrden repo, UnidadDeTrabajo tx, PoliticaReintentos politica,
            @Value("${orden.lease}") Duration lease,
            @Value("${orden.planificador.lote:16}") int lote) {
        return new ServicioContinuarSaga(serviciosSaga, repo, tx, politica, lease, lote);
    }

    @Bean
    ServicioIniciarTramitacion servicioIniciarTramitacion(RepositorioOrden repo, UnidadDeTrabajo tx) {
        return new ServicioIniciarTramitacion(repo, tx);
    }

    @Bean
    ServicioSoporteSagas servicioSoporteSagas(RepositorioOrden repo, UnidadDeTrabajo tx,
            PuertoConsultaSagasSoporte consultas) {
        return new ServicioSoporteSagas(repo, tx, consultas);
    }

    @Bean
    ServicioTicketsSoporte servicioTicketsSoporte(PuertoSagasTicketPendiente pendientes,
            PuertoTicketsSoporte tickets, RepositorioOrden repo, UnidadDeTrabajo tx) {
        return new ServicioTicketsSoporte(pendientes, tickets, repo, tx);
    }

    @Bean
    ServicioRegistrarRespuestaSecundaria2 servicioRegistrarRespuestaSecundaria2(RepositorioOrden repo,
            UnidadDeTrabajo tx, PuertoMensajesProcesados dedup, PoliticaReintentos politica) {
        return new ServicioRegistrarRespuestaSecundaria2(repo, tx, dedup, politica);
    }

    @Bean
    ServicioLimpiezaDatos servicioLimpiezaDatos(RepositorioOrden repo, PuertoMensajesProcesados dedup,
            UnidadDeTrabajo tx) {
        return new ServicioLimpiezaDatos(repo, dedup, tx);
    }
}
