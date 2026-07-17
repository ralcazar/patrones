package com.ejemplo.app.infraestructure.ordermanager;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoConsultaOrdenesSoporte;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoMensajesProcesados;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoOrdenesTicketPendiente;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoTicketsSoporte;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ServicioContinuarOrden;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ServicioLimpiezaDatos;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ProcesadorOrden;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ServicioSoporteOrdenes;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ServicioTicketsSoporte;
import com.ejemplo.app.business.ordermanager.dominio.PoliticaReintentos;

/**
 * Wiring del motor de órdenes: genérico en el tipo de orden, no conoce las
 * sagas concretas (ver {@link ConfiguracionSagas} en {@code infraestructure.sagas}).
 * Los procesadores de orden que participan del bucle de continuación llegan
 * aquí como {@code List<ProcesadorOrden>}: cada uno se registra a sí mismo por
 * su {@code tipo()}, sin que este motor tenga que enumerarlos a mano.
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
    ServicioContinuarOrden servicioContinuarOrden(List<ProcesadorOrden> procesadores,
            RepositorioOrden repo, PoliticaReintentos politica,
            @Value("${orden.lease}") Duration lease,
            @Value("${orden.planificador.lote:16}") int lote,
            @Lazy ServicioContinuarOrden self) {
        var procesadoresPorTipo = procesadores.stream()
                .collect(Collectors.toUnmodifiableMap(ProcesadorOrden::tipo, s -> s));
        var servicio = new ServicioContinuarOrden(procesadoresPorTipo, repo, politica, lease, lote);
        servicio.establecerSelf(self);
        return servicio;
    }

    @Bean
    ServicioSoporteOrdenes servicioSoporteOrdenes(RepositorioOrden repo, PuertoConsultaOrdenesSoporte consultas) {
        return new ServicioSoporteOrdenes(repo, consultas);
    }

    @Bean
    ServicioTicketsSoporte servicioTicketsSoporte(PuertoOrdenesTicketPendiente pendientes,
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
