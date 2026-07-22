package com.ejemplo.app.infraestructure.sagas.sagaprincipal.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.SagaPrincipal;
import com.ejemplo.app.infraestructure.ordermanager.persistencia.OrdenEntity;
import com.ejemplo.app.infraestructure.ordermanager.persistencia.OrdenJpaRepository;

/**
 * {@link AdaptadorBusquedaTramitacion}: el repo JPA (Spring Data) del motor
 * se dobla con Mockito, igual que {@link SoporteSagaPrincipalTest} - test de
 * infraestructure, no de business (ver CLAUDE.md).
 */
class AdaptadorBusquedaTramitacionTest {

    private final OrdenJpaRepository repo = mock(OrdenJpaRepository.class);
    private final AdaptadorBusquedaTramitacion adaptador = new AdaptadorBusquedaTramitacion(repo);

    @Test
    void ordenPrincipalDe_existente_devuelveElOrdenIdMapeadoYConsultaPorElTipoPrincipal() {
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var ordenId = OrdenId.nuevo();
        var ahora = Instant.now();
        var entity = new OrdenEntity(ordenId.valor(), "PRINCIPAL", externalId.valor().toString(), "INICIAL", 0,
                List.of(), 0, ahora, null, null, null, null, null, null, 0L, ahora, ahora);
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
