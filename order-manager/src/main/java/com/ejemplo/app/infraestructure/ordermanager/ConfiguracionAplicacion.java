package com.ejemplo.app.infraestructure.ordermanager;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoConciliacionSecundaria2;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoConsultaSagasSoporte;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoMensajesProcesados;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoPaso1;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoPaso2;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoPaso3;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoPaso4;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoPaso5;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoPaso6;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoPaso7;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoPaso8;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoSagaSecundaria1;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoSagaSecundaria2;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoSagaSecundaria3;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoSagasTicketPendiente;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoTicketsSoporte;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.comun.ServicioContinuarSaga;
import com.ejemplo.app.business.sagas.aplicacion.servicio.comun.ServicioIniciarTramitacion;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.comun.ServicioLimpiezaDatos;
import com.ejemplo.app.business.sagas.aplicacion.servicio.comun.ServicioRegistrarRespuestaSecundaria2;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.comun.ServicioSaga;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.comun.ServicioSoporteSagas;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.comun.ServicioTicketsSoporte;
import com.ejemplo.app.business.sagas.aplicacion.servicio.sagaprincipal.ServicioSagaPrincipal;
import com.ejemplo.app.business.sagas.aplicacion.servicio.sagasecundaria1.ServicioSagaSecundaria1;
import com.ejemplo.app.business.sagas.aplicacion.servicio.sagasecundaria2.ServicioSagaSecundaria2;
import com.ejemplo.app.business.sagas.aplicacion.servicio.sagasecundaria3.ServicioSagaSecundaria3;
import com.ejemplo.app.business.ordermanager.dominio.comun.PoliticaReintentos;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;

/**
 * Wiring de los servicios de aplicación (POJOs sin anotaciones Spring: la capa
 * business no depende de ningún framework). Los adaptadores de los puertos
 * REST de paso (PuertoPasoN, PuertoSagaSecundariaN, PuertoConciliacionSecundaria2)
 * y de deduplicación (PuertoMensajesProcesados) no están implementados en este
 * esqueleto: Spring los inyectará cuando existan.
 *
 * La frontera transaccional es {@code @Transactional} (jakarta.transaction)
 * directamente sobre métodos de estos POJOs: Spring envuelve en un proxy
 * transaccional también los beans devueltos por métodos {@code @Bean}. Los
 * servicios de saga (y otros con REST fuera de tx) necesitan invocar su parte
 * transaccional A TRAVÉS de ese proxy (una auto-invocación normal lo
 * saltaría), así que aquí se les inyecta la referencia a sí mismos con un
 * parámetro {@code @Lazy} del mismo tipo que el bean que el método produce:
 * Spring resuelve ese parámetro con un proxy perezoso del propio bean.
 */
@Configuration
public class ConfiguracionAplicacion {

    @Bean
    PoliticaReintentos politicaReintentos() {
        return new PoliticaReintentos();
    }

    @Bean
    ServicioSagaPrincipal servicioSagaPrincipal(RepositorioOrden repo,
            @Value("${orden.lease}") Duration lease,
            PuertoPaso1 p1, PuertoPaso2 p2, PuertoPaso3 p3, PuertoPaso4 p4,
            PuertoPaso5 p5, PuertoPaso6 p6, PuertoPaso7 p7, PuertoPaso8 p8,
            @Lazy ServicioSagaPrincipal self) {
        var servicio = new ServicioSagaPrincipal(repo, lease, p1, p2, p3, p4, p5, p6, p7, p8);
        servicio.establecerSelf(self);
        return servicio;
    }

    @Bean
    ServicioSagaSecundaria1 servicioSagaSecundaria1(RepositorioOrden repo,
            @Value("${orden.lease}") Duration lease, PuertoSagaSecundaria1 puerto,
            @Lazy ServicioSagaSecundaria1 self) {
        var servicio = new ServicioSagaSecundaria1(repo, lease, puerto);
        servicio.establecerSelf(self);
        return servicio;
    }

    @Bean
    ServicioSagaSecundaria2 servicioSagaSecundaria2(RepositorioOrden repo,
            PuertoSagaSecundaria2 puerto, PuertoConciliacionSecundaria2 conciliacion,
            @Lazy ServicioSagaSecundaria2 self) {
        var servicio = new ServicioSagaSecundaria2(repo, puerto, conciliacion);
        servicio.establecerSelf(self);
        return servicio;
    }

    @Bean
    ServicioSagaSecundaria3 servicioSagaSecundaria3(RepositorioOrden repo, PuertoSagaSecundaria3 puerto,
            @Lazy ServicioSagaSecundaria3 self) {
        var servicio = new ServicioSagaSecundaria3(repo, puerto);
        servicio.establecerSelf(self);
        return servicio;
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
            RepositorioOrden repo, PoliticaReintentos politica,
            @Value("${orden.lease}") Duration lease,
            @Value("${orden.planificador.lote:16}") int lote,
            @Lazy ServicioContinuarSaga self) {
        var servicio = new ServicioContinuarSaga(serviciosSaga, repo, politica, lease, lote);
        servicio.establecerSelf(self);
        return servicio;
    }

    @Bean
    ServicioIniciarTramitacion servicioIniciarTramitacion(RepositorioOrden repo) {
        return new ServicioIniciarTramitacion(repo);
    }

    @Bean
    ServicioSoporteSagas servicioSoporteSagas(RepositorioOrden repo, PuertoConsultaSagasSoporte consultas) {
        return new ServicioSoporteSagas(repo, consultas);
    }

    @Bean
    ServicioTicketsSoporte servicioTicketsSoporte(PuertoSagasTicketPendiente pendientes,
            PuertoTicketsSoporte tickets, RepositorioOrden repo, @Lazy ServicioTicketsSoporte self) {
        var servicio = new ServicioTicketsSoporte(pendientes, tickets, repo);
        servicio.establecerSelf(self);
        return servicio;
    }

    @Bean
    ServicioRegistrarRespuestaSecundaria2 servicioRegistrarRespuestaSecundaria2(RepositorioOrden repo,
            PuertoMensajesProcesados dedup, PoliticaReintentos politica) {
        return new ServicioRegistrarRespuestaSecundaria2(repo, dedup, politica);
    }

    @Bean
    ServicioLimpiezaDatos servicioLimpiezaDatos(RepositorioOrden repo, PuertoMensajesProcesados dedup) {
        return new ServicioLimpiezaDatos(repo, dedup);
    }
}
