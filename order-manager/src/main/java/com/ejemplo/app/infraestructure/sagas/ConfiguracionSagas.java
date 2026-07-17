package com.ejemplo.app.infraestructure.sagas;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoConsultaSagasSoporte;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoMensajesProcesados;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.dominio.comun.PoliticaReintentos;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoConciliacionSecundaria2;
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
import com.ejemplo.app.business.sagas.aplicacion.servicio.comun.ServicioCancelarTramitacion;
import com.ejemplo.app.business.sagas.aplicacion.servicio.comun.ServicioIniciarTramitacion;
import com.ejemplo.app.business.sagas.aplicacion.servicio.comun.ServicioRegistrarRespuestaSecundaria2;
import com.ejemplo.app.business.sagas.aplicacion.servicio.comun.ServicioVistaTramitacion;
import com.ejemplo.app.business.sagas.aplicacion.servicio.sagaprincipal.ServicioSagaPrincipal;
import com.ejemplo.app.business.sagas.aplicacion.servicio.sagasecundaria1.ServicioSagaSecundaria1;
import com.ejemplo.app.business.sagas.aplicacion.servicio.sagasecundaria2.ServicioSagaSecundaria2;
import com.ejemplo.app.business.sagas.aplicacion.servicio.sagasecundaria3.ServicioSagaSecundaria3;

/**
 * Wiring de las 4 sagas concretas: se apoya en los puertos y servicios
 * genéricos del motor ({@code RepositorioOrden}, {@code PoliticaReintentos},
 * ...) definidos en {@code infraestructure.ordermanager}, pero el motor
 * nunca depende de esta clase ni de las sagas (ver {@link
 * com.ejemplo.app.infraestructure.ordermanager.ConfiguracionOrderManager}).
 *
 * Mismo patrón de auto-inyección {@code @Lazy self} que el resto de servicios
 * con REST fuera de transacción (ver la javadoc de ConfiguracionOrderManager).
 */
@Configuration
public class ConfiguracionSagas {

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
    ServicioIniciarTramitacion servicioIniciarTramitacion(RepositorioOrden repo) {
        return new ServicioIniciarTramitacion(repo);
    }

    @Bean
    ServicioRegistrarRespuestaSecundaria2 servicioRegistrarRespuestaSecundaria2(RepositorioOrden repo,
            PuertoMensajesProcesados dedup, PoliticaReintentos politica) {
        return new ServicioRegistrarRespuestaSecundaria2(repo, dedup, politica);
    }

    @Bean
    ServicioCancelarTramitacion servicioCancelarTramitacion(RepositorioOrden repo) {
        return new ServicioCancelarTramitacion(repo);
    }

    @Bean
    ServicioVistaTramitacion servicioVistaTramitacion(PuertoConsultaSagasSoporte consultas) {
        return new ServicioVistaTramitacion(consultas);
    }
}
