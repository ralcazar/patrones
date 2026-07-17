package com.ejemplo.app.infraestructure.ordermanager;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoConsultaSagasSoporte;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoMensajesProcesados;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoSagasTicketPendiente;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoTicketsSoporte;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.comun.ServicioContinuarSaga;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.comun.ServicioLimpiezaDatos;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.comun.ServicioSaga;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.comun.ServicioSoporteSagas;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.comun.ServicioTicketsSoporte;
import com.ejemplo.app.business.ordermanager.dominio.comun.PoliticaReintentos;

/**
 * Wiring del motor de órdenes: genérico en el tipo de saga, no conoce las
 * sagas concretas (ver {@link ConfiguracionSagas} en {@code infraestructure.sagas}).
 * Los servicios de saga que participan del bucle de continuación llegan aquí
 * como {@code List<ServicioSaga>}: cada uno se registra a sí mismo por su
 * {@code tipo()}, sin que este motor tenga que enumerarlos a mano.
 *
 * La frontera transaccional es {@code @Transactional} (jakarta.transaction)
 * directamente sobre métodos de estos POJOs: Spring envuelve en un proxy
 * transaccional también los beans devueltos por métodos {@code @Bean}. Los
 * servicios con REST fuera de tx necesitan invocar su parte transaccional A
 * TRAVÉS de ese proxy (una auto-invocación normal lo saltaría), así que aquí
 * se les inyecta la referencia a sí mismos con un parámetro {@code @Lazy}
 * del mismo tipo que el bean que el método produce: Spring resuelve ese
 * parámetro con un proxy perezoso del propio bean.
 */
@Configuration
public class ConfiguracionOrderManager {

    @Bean
    PoliticaReintentos politicaReintentos() {
        return new PoliticaReintentos();
    }

    @Bean
    ServicioContinuarSaga servicioContinuarSaga(List<ServicioSaga> serviciosSaga,
            RepositorioOrden repo, PoliticaReintentos politica,
            @Value("${orden.lease}") Duration lease,
            @Value("${orden.planificador.lote:16}") int lote,
            @Lazy ServicioContinuarSaga self) {
        var serviciosSagaPorTipo = serviciosSaga.stream()
                .collect(Collectors.toUnmodifiableMap(ServicioSaga::tipo, s -> s));
        var servicio = new ServicioContinuarSaga(serviciosSagaPorTipo, repo, politica, lease, lote);
        servicio.establecerSelf(self);
        return servicio;
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
    ServicioLimpiezaDatos servicioLimpiezaDatos(RepositorioOrden repo, PuertoMensajesProcesados dedup) {
        return new ServicioLimpiezaDatos(repo, dedup);
    }
}
