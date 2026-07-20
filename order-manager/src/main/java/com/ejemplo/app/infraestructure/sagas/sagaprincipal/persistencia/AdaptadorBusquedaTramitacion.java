package com.ejemplo.app.infraestructure.sagas.sagaprincipal.persistencia;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoBusquedaTramitacion;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.SagaPrincipal;
import com.ejemplo.app.infraestructure.ordermanager.persistencia.OrdenJpaRepository;

/** Busca directamente sobre {@code orden} (tabla común de la FSM, fusionada con la ejecución), sin cargar el agregado completo. */
@Component
public class AdaptadorBusquedaTramitacion implements PuertoBusquedaTramitacion {

    private final OrdenJpaRepository repo;

    public AdaptadorBusquedaTramitacion(OrdenJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public Optional<OrdenId> ordenPrincipalDe(ExternalId externalId) {
        return repo.findByExternalIdAndTipo(externalId.valor().toString(), SagaPrincipal.TIPO.valor())
                .map(entity -> new OrdenId(entity.getOrdenId()));
    }
}
