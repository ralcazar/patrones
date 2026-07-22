package com.ejemplo.app.infraestructure.ordermanager.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Entidad JPA de OrdenRoot: los getters y el bookkeeping de {@code creadaEn}/
 * {@code actualizadaEn} se pueden probar como Java puro, sin arrancar Spring
 * ni JPA (los callbacks {@code @PrePersist}/{@code @PreUpdate} son métodos
 * normales de instancia, invocables directamente). Tras la fusión de
 * orden+proceso, la entidad también lleva el estado de negocio (tipo,
 * externalId, estado, auditoria).
 */
class OrdenEntityTest {

    @Test
    void constructorYGetters_exponenTodosLosCampos() {
        var ordenId = UUID.randomUUID();
        var proximoReintentoEn = Instant.now();
        var tokenExpiraEn = proximoReintentoEn.plusSeconds(600);
        var ticketAbiertoEn = proximoReintentoEn.minusSeconds(60);
        var completadaEn = proximoReintentoEn.plusSeconds(120);
        var auditoria = List.of(new AuditoriaEntity(proximoReintentoEn, "ana", "CANCELAR", "motivo"));
        var entity = new OrdenEntity(ordenId, "PRINCIPAL", "ext-1", "INICIAL", 0, auditoria, 3, proximoReintentoEn,
                "token-1", tokenExpiraEn, ticketAbiertoEn, completadaEn, "java.lang.RuntimeException", "boom", 5L);

        assertThat(entity.getOrdenId()).isEqualTo(ordenId);
        assertThat(entity.getTipo()).isEqualTo("PRINCIPAL");
        assertThat(entity.getExternalId()).isEqualTo("ext-1");
        assertThat(entity.getEstado()).isEqualTo("INICIAL");
        assertThat(entity.getAuditoria()).isEqualTo(auditoria);
        assertThat(entity.getIntentos()).isEqualTo(3);
        assertThat(entity.getProximoReintentoEn()).isEqualTo(proximoReintentoEn);
        assertThat(entity.getTokenTrabajador()).isEqualTo("token-1");
        assertThat(entity.getTokenExpiraEn()).isEqualTo(tokenExpiraEn);
        assertThat(entity.getTicketAbiertoEn()).isEqualTo(ticketAbiertoEn);
        assertThat(entity.getCompletadaEn()).isEqualTo(completadaEn);
        assertThat(entity.getUltimoErrorTipo()).isEqualTo("java.lang.RuntimeException");
        assertThat(entity.getUltimoErrorMensaje()).isEqualTo("boom");
        assertThat(entity.getVersion()).isEqualTo(5L);
    }

    @Test
    void alCrear_fijaCreadaEnSoloLaPrimeraVez() {
        var entity = new OrdenEntity(UUID.randomUUID(), "PRINCIPAL", "ext-1", "INICIAL", 0, List.of(),
                0, Instant.now(), null, null, null, null, null, null, 0L);

        entity.alCrear();
        var creadaEnOriginal = entity.getCreadaEn();
        assertThat(creadaEnOriginal).isNotNull();

        entity.alCrear(); // una segunda invocación no debe pisar creadaEn ya fijado

        assertThat(entity.getCreadaEn()).isEqualTo(creadaEnOriginal);
    }

    @Test
    void alActualizar_refrescaActualizadaEn() {
        var entity = new OrdenEntity(UUID.randomUUID(), "PRINCIPAL", "ext-1", "INICIAL", 0, List.of(),
                0, Instant.now(), null, null, null, null, null, null, 0L);
        entity.alCrear();
        var actualizadaEnTrasCrear = entity.getActualizadaEn();

        entity.alActualizar();

        assertThat(entity.getActualizadaEn()).isNotNull();
        assertThat(entity.getActualizadaEn()).isAfterOrEqualTo(actualizadaEnTrasCrear);
    }

    @Test
    void persistable_getIdDevuelveElOrdenIdYMarcarComoNuevaActivaIsNew() {
        var ordenId = UUID.randomUUID();
        var entity = new OrdenEntity(ordenId, "PRINCIPAL", "ext-1", "INICIAL", 0, List.of(),
                0, Instant.now(), null, null, null, null, null, null, 0L);

        assertThat(entity.getId()).as("Persistable.getId() delega en el mismo ordenId").isEqualTo(ordenId);
        assertThat(entity.isNew()).as("por defecto no es nueva (la usa guardar(), que va por merge())").isFalse();

        entity.marcarComoNueva();

        assertThat(entity.isNew()).as("tras marcarComoNueva() (la usa crear(), fuerza persist())").isTrue();
    }
}
