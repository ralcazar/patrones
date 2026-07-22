package com.ejemplo.app.infraestructure.ordermanager.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Entidad JPA de OrdenRoot: los getters se pueden probar como Java puro, sin
 * arrancar Spring ni JPA. Tras la fusión de orden+proceso, la entidad también
 * lleva el estado de negocio (tipo, externalId, estado, auditoria).
 * {@code creadaEn}/{@code actualizadaEn} las aporta el dominio (OrdenRoot):
 * esta entidad solo las transporta, ya no las fija por su cuenta (no hay
 * {@code @PrePersist}/{@code @PreUpdate}).
 */
class OrdenEntityTest {

    @Test
    void constructorYGetters_exponenTodosLosCampos() {
        var ordenId = UUID.randomUUID();
        var proximoReintentoEn = Instant.now();
        var tokenExpiraEn = proximoReintentoEn.plusSeconds(600);
        var ticketAbiertoEn = proximoReintentoEn.minusSeconds(60);
        var completadaEn = proximoReintentoEn.plusSeconds(120);
        var creadaEn = proximoReintentoEn.minusSeconds(3600);
        var actualizadaEn = proximoReintentoEn.minusSeconds(30);
        var auditoria = List.of(new AuditoriaEntity(proximoReintentoEn, "ana", "CANCELAR", "motivo"));
        var entity = new OrdenEntity(ordenId, "PRINCIPAL", "ext-1", "INICIAL", 0, auditoria, 3, proximoReintentoEn,
                "token-1", tokenExpiraEn, ticketAbiertoEn, completadaEn, "java.lang.RuntimeException", "boom", 5L,
                creadaEn, actualizadaEn);

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
        assertThat(entity.getCreadaEn()).isEqualTo(creadaEn);
        assertThat(entity.getActualizadaEn()).isEqualTo(actualizadaEn);
    }

    @Test
    void persistable_getIdDevuelveElOrdenIdYMarcarComoNuevaActivaIsNew() {
        var ordenId = UUID.randomUUID();
        var ahora = Instant.now();
        var entity = new OrdenEntity(ordenId, "PRINCIPAL", "ext-1", "INICIAL", 0, List.of(),
                0, ahora, null, null, null, null, null, null, 0L, ahora, ahora);

        assertThat(entity.getId()).as("Persistable.getId() delega en el mismo ordenId").isEqualTo(ordenId);
        assertThat(entity.isNew()).as("por defecto no es nueva (la usa guardar(), que va por merge())").isFalse();

        entity.marcarComoNueva();

        assertThat(entity.isNew()).as("tras marcarComoNueva() (la usa crear(), fuerza persist())").isTrue();
    }
}
