package com.ejemplo.app.infraestructure.ordermanager;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoColaTareas;
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
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoTicketsSoporte;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioSagaPrincipal;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioSagaSecundaria1;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioSagaSecundaria2;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioSagaSecundaria3;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.UnidadDeTrabajo;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ManejadorTareasSaga;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ServicioEncolarTramitacion;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ServicioLimpiezaDatos;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ServicioSagaPrincipal;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ServicioSagaSecundaria1;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ServicioSagaSecundaria2;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ServicioSagaSecundaria3;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ServicioSoporteSagas;

/**
 * Wiring de los servicios de aplicación (POJOs sin anotaciones Spring: la capa
 * business no depende de ningún framework). Los adaptadores de los puertos
 * REST, repositorios y tickets no están implementados en este esqueleto:
 * Spring los inyectará cuando existan.
 */
@Configuration
public class ConfiguracionAplicacion {

    @Bean
    ServicioSagaPrincipal servicioSagaPrincipal(RepositorioSagaPrincipal repo,
            RepositorioSagaSecundaria1 repoSecundaria1, RepositorioSagaSecundaria2 repoSecundaria2,
            RepositorioSagaSecundaria3 repoSecundaria3, UnidadDeTrabajo tx,
            PuertoMensajesProcesados dedup, PuertoColaTareas cola, PuertoTicketsSoporte tickets,
            PuertoPaso1 p1, PuertoPaso2 p2, PuertoPaso3 p3, PuertoPaso4 p4,
            PuertoPaso5 p5, PuertoPaso6 p6, PuertoPaso7 p7, PuertoPaso8 p8) {
        return new ServicioSagaPrincipal(repo, repoSecundaria1, repoSecundaria2, repoSecundaria3,
                tx, dedup, cola, tickets, p1, p2, p3, p4, p5, p6, p7, p8);
    }

    @Bean
    ServicioSagaSecundaria1 servicioSagaSecundaria1(RepositorioSagaSecundaria1 repo, UnidadDeTrabajo tx,
            PuertoMensajesProcesados dedup, PuertoColaTareas cola, PuertoTicketsSoporte tickets,
            PuertoSagaSecundaria1 puerto) {
        return new ServicioSagaSecundaria1(repo, tx, dedup, cola, tickets, puerto);
    }

    @Bean
    ServicioSagaSecundaria2 servicioSagaSecundaria2(RepositorioSagaSecundaria2 repo, UnidadDeTrabajo tx,
            PuertoMensajesProcesados dedup, PuertoColaTareas cola, PuertoTicketsSoporte tickets,
            PuertoSagaSecundaria2 puerto) {
        return new ServicioSagaSecundaria2(repo, tx, dedup, cola, tickets, puerto);
    }

    @Bean
    ServicioSagaSecundaria3 servicioSagaSecundaria3(RepositorioSagaSecundaria3 repo, UnidadDeTrabajo tx,
            PuertoMensajesProcesados dedup, PuertoColaTareas cola, PuertoTicketsSoporte tickets,
            PuertoSagaSecundaria3 puerto) {
        return new ServicioSagaSecundaria3(repo, tx, dedup, cola, tickets, puerto);
    }

    @Bean
    ManejadorTareasSaga manejadorTareasSaga(ServicioSagaPrincipal principal,
            ServicioSagaSecundaria1 secundaria1, ServicioSagaSecundaria2 secundaria2,
            ServicioSagaSecundaria3 secundaria3) {
        return new ManejadorTareasSaga(principal, secundaria1, secundaria2, secundaria3);
    }

    @Bean
    ServicioSoporteSagas servicioSoporteSagas(ServicioSagaPrincipal principal,
            ServicioSagaSecundaria1 secundaria1, ServicioSagaSecundaria2 secundaria2,
            ServicioSagaSecundaria3 secundaria3, PuertoConsultaSagasSoporte consultas) {
        return new ServicioSoporteSagas(principal, secundaria1, secundaria2, secundaria3, consultas);
    }

    @Bean
    ServicioEncolarTramitacion servicioEncolarTramitacion(PuertoColaTareas cola) {
        return new ServicioEncolarTramitacion(cola);
    }

    @Bean
    ServicioLimpiezaDatos servicioLimpiezaDatos(RepositorioSagaPrincipal repoPrincipal,
            RepositorioSagaSecundaria1 repoSecundaria1, RepositorioSagaSecundaria2 repoSecundaria2,
            RepositorioSagaSecundaria3 repoSecundaria3, PuertoMensajesProcesados dedup,
            PuertoColaTareas cola, UnidadDeTrabajo tx) {
        return new ServicioLimpiezaDatos(repoPrincipal, repoSecundaria1, repoSecundaria2,
                repoSecundaria3, dedup, cola, tx);
    }
}
