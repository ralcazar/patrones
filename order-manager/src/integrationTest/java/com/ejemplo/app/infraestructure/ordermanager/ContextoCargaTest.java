package com.ejemplo.app.infraestructure.ordermanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.TestPropertySource;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoConsultaOrdenesSoporte;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoObservadorEjecucion;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoOrdenesTicketPendiente;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoTicketsSoporte;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ServicioContinuarOrden;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ServicioLimpiezaDatos;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ServicioSoporteOrdenes;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ServicioTicketsSoporte;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoBusquedaTramitacion;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoConciliacionSecundaria2;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoDatosNegocio;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.RepositorioDatosNegocio;
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
import com.ejemplo.app.business.sagas.aplicacion.servicio.comun.ServicioPurgarDatosNegocioHuerfanos;
import com.ejemplo.app.business.sagas.aplicacion.servicio.sagasecundaria2.ServicioRegistrarRespuestaSecundaria2;
import com.ejemplo.app.business.sagas.aplicacion.servicio.comun.ServicioVistaTramitacion;
import com.ejemplo.app.business.sagas.aplicacion.servicio.sagaprincipal.ServicioSagaPrincipal;
import com.ejemplo.app.business.sagas.aplicacion.servicio.sagasecundaria1.ServicioSagaSecundaria1;
import com.ejemplo.app.business.sagas.aplicacion.servicio.sagasecundaria2.ServicioSagaSecundaria2;
import com.ejemplo.app.business.sagas.aplicacion.servicio.sagasecundaria3.ServicioSagaSecundaria3;
import com.ejemplo.app.infraestructure.ordermanager.programados.ConfiguracionEjecucionAsincrona;
import com.ejemplo.app.infraestructure.sagas.comun.ConfiguracionSagas;

/**
 * Arranca el contexto Spring real de {@link ConfiguracionOrderManager},
 * {@link ConfiguracionSagas} y {@link ConfiguracionEjecucionAsincrona} con
 * dobles de Mockito para los puertos de salida (todavía sin adaptador real):
 * cubre el wiring (los métodos {@code @Bean} y {@code configureTasks}) que
 * ningún test unitario ejercita, sin arrancar BD ni ningún broker.
 */
@SpringBootTest(classes = {
        ConfiguracionOrderManager.class,
        ConfiguracionSagas.class,
        ConfiguracionEjecucionAsincrona.class,
        ContextoCargaTest.PuertosDeSalidaFalsos.class
})
@TestPropertySource(properties = "ordermanager.lease=PT10M")
class ContextoCargaTest {

    @Autowired
    private ApplicationContext contexto;

    @Test
    void elContextoArrancaConTodosLosServiciosDeAplicacionCableados() {
        assertThat(contexto.getBean(ServicioContinuarOrden.class)).isNotNull();
        assertThat(contexto.getBean(ServicioSoporteOrdenes.class)).isNotNull();
        assertThat(contexto.getBean(ServicioTicketsSoporte.class)).isNotNull();
        assertThat(contexto.getBean(ServicioLimpiezaDatos.class)).isNotNull();
        assertThat(contexto.getBean(ServicioSagaPrincipal.class)).isNotNull();
        assertThat(contexto.getBean(ServicioSagaSecundaria1.class)).isNotNull();
        assertThat(contexto.getBean(ServicioSagaSecundaria2.class)).isNotNull();
        assertThat(contexto.getBean(ServicioSagaSecundaria3.class)).isNotNull();
        assertThat(contexto.getBean(ServicioIniciarTramitacion.class)).isNotNull();
        assertThat(contexto.getBean(ServicioRegistrarRespuestaSecundaria2.class)).isNotNull();
        assertThat(contexto.getBean(ServicioCancelarTramitacion.class)).isNotNull();
        assertThat(contexto.getBean(ServicioVistaTramitacion.class)).isNotNull();
        assertThat(contexto.getBean(ServicioPurgarDatosNegocioHuerfanos.class)).isNotNull();
        assertThat(contexto.getBean("ejecutorContinuacion", ThreadPoolTaskExecutor.class)).isNotNull();
    }

    /** Dobles de Mockito de los puertos de salida: aún sin adaptador real, solo hace falta que el contexto arranque. */
    @Configuration
    static class PuertosDeSalidaFalsos {

        @Bean RepositorioOrden repositorioOrden() { return mock(RepositorioOrden.class); }
        @Bean PuertoConsultaOrdenesSoporte puertoConsultaOrdenesSoporte() { return mock(PuertoConsultaOrdenesSoporte.class); }
        @Bean PuertoOrdenesTicketPendiente puertoOrdenesTicketPendiente() { return mock(PuertoOrdenesTicketPendiente.class); }
        @Bean PuertoTicketsSoporte puertoTicketsSoporte() { return mock(PuertoTicketsSoporte.class); }
        @Bean PuertoObservadorEjecucion puertoObservadorEjecucion() { return mock(PuertoObservadorEjecucion.class); }

        @Bean PuertoPaso1 puertoPaso1() { return mock(PuertoPaso1.class); }
        @Bean PuertoPaso2 puertoPaso2() { return mock(PuertoPaso2.class); }
        @Bean PuertoPaso3 puertoPaso3() { return mock(PuertoPaso3.class); }
        @Bean PuertoPaso4 puertoPaso4() { return mock(PuertoPaso4.class); }
        @Bean PuertoPaso5 puertoPaso5() { return mock(PuertoPaso5.class); }
        @Bean PuertoPaso6 puertoPaso6() { return mock(PuertoPaso6.class); }
        @Bean PuertoPaso7 puertoPaso7() { return mock(PuertoPaso7.class); }
        @Bean PuertoPaso8 puertoPaso8() { return mock(PuertoPaso8.class); }

        @Bean PuertoSagaSecundaria1 puertoSagaSecundaria1() { return mock(PuertoSagaSecundaria1.class); }
        @Bean PuertoSagaSecundaria2 puertoSagaSecundaria2() { return mock(PuertoSagaSecundaria2.class); }
        @Bean PuertoSagaSecundaria3 puertoSagaSecundaria3() { return mock(PuertoSagaSecundaria3.class); }
        @Bean PuertoConciliacionSecundaria2 puertoConciliacionSecundaria2() { return mock(PuertoConciliacionSecundaria2.class); }

        @Bean PuertoDatosNegocio puertoDatosNegocio() { return mock(PuertoDatosNegocio.class); }
        @Bean RepositorioDatosNegocio repositorioDatosNegocio() { return mock(RepositorioDatosNegocio.class); }
        @Bean PuertoBusquedaTramitacion puertoBusquedaTramitacion() { return mock(PuertoBusquedaTramitacion.class); }
    }
}
