package com.ejemplo.app.infraestructure.sagas.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.SagaPrincipal;
import com.ejemplo.app.infraestructure.ordermanager.persistencia.ProcesoEntity;
import com.ejemplo.app.infraestructure.ordermanager.persistencia.ProcesoJpaRepository;

/**
 * {@link AdaptadorBusquedaTramitacion}: el repo JPA (Spring Data) del motor
 * se dobla con Mockito, igual que {@link SoporteSagaPrincipalTest} - test de
 * infraestructure, no de business (ver CLAUDE.md).
 */
class AdaptadorBusquedaTramitacionTest {

    private final ProcesoJpaRepository repo = mock(ProcesoJpaRepository.class);
    private final AdaptadorBusquedaTramitacion adaptador = new AdaptadorBusquedaTramitacion(repo);

    @Test
    void ordenPrincipalDe_existente_devuelveElOrdenIdMapeadoYConsultaPorElTipoPrincipal() {
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var ordenId = OrdenId.nuevo();
        var entity = new ProcesoEntity(ordenId.valor(), "PRINCIPAL", externalId.valor().toString(), "INICIAL",
                List.of());
        when(repo.findByExternalIdAndTipo(externalId.valor().toString(), SagaPrincipal.TIPO.valor()))
                .thenReturn(Optional.of(entity));

        var encontrado = adaptador.ordenPrincipalDe(externalId);

        assertThat(encontrado).contains(ordenId);
        verify(repo).findByExternalIdAndTipo(externalId.valor().toString(), "PRINCIPAL");
    }

    @Test
    void ordenPrincipalDe_inexistente_devuelveVacio() {
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        when(repo.findByExternalIdAndTipo(externalId.valor().toString(), SagaPrincipal.TIPO.valor()))
                .thenReturn(Optional.empty());

        assertThat(adaptador.ordenPrincipalDe(externalId)).isEmpty();
    }
}
