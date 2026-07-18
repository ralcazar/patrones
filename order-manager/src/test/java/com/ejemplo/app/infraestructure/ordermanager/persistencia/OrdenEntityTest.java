package com.ejemplo.app.infraestructure.ordermanager.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Entidad JPA de OrdenRoot: los getters y el bookkeeping de {@code creadaEn}/
 * {@code actualizadaEn} se pueden probar como Java puro, sin arrancar Spring
 * ni JPA (los callbacks {@code @PrePersist}/{@code @PreUpdate} son métodos
 * normales de instancia, invocables directamente).
 */
class OrdenEntityTest {

    @Test
    void constructorYGetters_exponenTodosLosCampos() {
        var ordenId = UUID.randomUUID();
        var proximoReintentoEn = Instant.now();
        var tokenExpiraEn = proximoReintentoEn.plusSeconds(600);
        var ticketAbiertoEn = proximoReintentoEn.minusSeconds(60);
        var completadaEn = proximoReintentoEn.plusSeconds(120);
        var entity = new OrdenEntity(ordenId, 3, proximoReintentoEn, "token-1", tokenExpiraEn,
                ticketAbiertoEn, completadaEn, 5L);

        assertThat(entity.getOrdenId()).isEqualTo(ordenId);
        assertThat(entity.getIntentos()).isEqualTo(3);
        assertThat(entity.getProximoReintentoEn()).isEqualTo(proximoReintentoEn);
        assertThat(entity.getTokenTrabajador()).isEqualTo("token-1");
        assertThat(entity.getTokenExpiraEn()).isEqualTo(tokenExpiraEn);
        assertThat(entity.getTicketAbiertoEn()).isEqualTo(ticketAbiertoEn);
        assertThat(entity.getCompletadaEn()).isEqualTo(completadaEn);
        assertThat(entity.getVersion()).isEqualTo(5L);
    }

    @Test
    void alCrear_fijaCreadaEnSoloLaPrimeraVez() {
        var entity = new OrdenEntity(UUID.randomUUID(), 0, Instant.now(), null, null, null, null, 0L);

        entity.alCrear();
        var creadaEnOriginal = entity.getCreadaEn();
        assertThat(creadaEnOriginal).isNotNull();

        entity.alCrear(); // una segunda invocación no debe pisar creadaEn ya fijado

        assertThat(entity.getCreadaEn()).isEqualTo(creadaEnOriginal);
    }

    @Test
    void alActualizar_refrescaActualizadaEn() {
        var entity = new OrdenEntity(UUID.randomUUID(), 0, Instant.now(), null, null, null, null, 0L);
        entity.alCrear();
        var actualizadaEnTrasCrear = entity.getActualizadaEn();

        entity.alActualizar();

        assertThat(entity.getActualizadaEn()).isNotNull();
        assertThat(entity.getActualizadaEn()).isAfterOrEqualTo(actualizadaEnTrasCrear);
    }
}
