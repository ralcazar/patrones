package com.ejemplo.app.business.ordermanager.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Contrato de igualdad de {@link Proceso}, el value object base común a las 4
 * sagas (ver {@link ProcesoFalso}, el doble de pruebas del motor): dos
 * instancias son iguales si y solo si coinciden id, externalId, estado y
 * auditoria. Cubre las 4 comparaciones (cortocircuito de {@code &&}) y los
 * casos límite de identidad/null/clase distinta.
 */
class ProcesoTest {

    private static final OrdenId ID = OrdenId.nuevo();
    private static final ExternalId EXTERNAL_ID = ExternalId.de(UUID.randomUUID().toString());
    private static final AuditoriaIntervencion AUDITORIA_1 = AuditoriaIntervencion.de(
            new UsuarioSoporte("ana"), "CANCELAR", "motivo");

    private static ProcesoFalso base() {
        return ProcesoFalso.rehidratar(ID, EXTERNAL_ID, ProcesoFalso.Estado.INICIAL, List.of());
    }

    @Test
    void equals_esReflexivo_mismaInstancia() {
        var proceso = base();

        assertThat(proceso.equals(proceso)).isTrue();
    }

    @Test
    void equals_conNull_esFalse() {
        assertThat(base().equals(null)).isFalse();
    }

    @Test
    void equals_conOtraClase_esFalse() {
        assertThat(base().equals("no soy un Proceso")).isFalse();
    }

    @Test
    void equals_conIdDistinto_esFalse() {
        var otro = ProcesoFalso.rehidratar(OrdenId.nuevo(), EXTERNAL_ID, ProcesoFalso.Estado.INICIAL, List.of());

        assertThat(base().equals(otro)).isFalse();
    }

    @Test
    void equals_conExternalIdDistinto_esFalse() {
        var otro = ProcesoFalso.rehidratar(ID, ExternalId.de(UUID.randomUUID().toString()),
                ProcesoFalso.Estado.INICIAL, List.of());

        assertThat(base().equals(otro)).isFalse();
    }

    @Test
    void equals_conEstadoDistinto_esFalse() {
        var otro = ProcesoFalso.rehidratar(ID, EXTERNAL_ID, ProcesoFalso.Estado.TERMINADO, List.of());

        assertThat(base().equals(otro)).isFalse();
    }

    @Test
    void equals_conAuditoriaDistinta_esFalse() {
        var otro = ProcesoFalso.rehidratar(ID, EXTERNAL_ID, ProcesoFalso.Estado.INICIAL, List.of(AUDITORIA_1));

        assertThat(base().equals(otro)).isFalse();
    }

    @Test
    void equals_conTodosLosCamposIguales_esTrue() {
        var uno = ProcesoFalso.rehidratar(ID, EXTERNAL_ID, ProcesoFalso.Estado.INICIAL, List.of(AUDITORIA_1));
        var otro = ProcesoFalso.rehidratar(ID, EXTERNAL_ID, ProcesoFalso.Estado.INICIAL, List.of(AUDITORIA_1));

        assertThat(uno).isEqualTo(otro);
        assertThat(uno.hashCode()).isEqualTo(otro.hashCode());
    }

    @Test
    void hashCode_conCamposDistintos_normalmenteDaHashesDistintos() {
        var otro = ProcesoFalso.rehidratar(OrdenId.nuevo(), EXTERNAL_ID, ProcesoFalso.Estado.INICIAL, List.of());

        assertThat(base().hashCode()).isNotEqualTo(otro.hashCode());
    }

    @Test
    void constructorRehidratacion_copiaLaAuditoriaEnVezDeCompartirLaReferencia() {
        var auditoriaMutable = new java.util.ArrayList<AuditoriaIntervencion>();
        auditoriaMutable.add(AUDITORIA_1);
        var proceso = ProcesoFalso.rehidratar(ID, EXTERNAL_ID, ProcesoFalso.Estado.INICIAL, auditoriaMutable);

        auditoriaMutable.add(AuditoriaIntervencion.de(new UsuarioSoporte("otro"), "OTRA", "otro detalle"));

        assertThat(proceso.auditoria()).hasSize(1);
    }

    @Test
    void instantesDeAuditoria_soloParaVerificarQueDeUsaInstantNow() {
        var antes = Instant.now();
        var entrada = AuditoriaIntervencion.de(new UsuarioSoporte("ana"), "CANCELAR", "motivo");
        var despues = Instant.now();

        assertThat(entrada.cuando()).isBetween(antes, despues);
    }
}
