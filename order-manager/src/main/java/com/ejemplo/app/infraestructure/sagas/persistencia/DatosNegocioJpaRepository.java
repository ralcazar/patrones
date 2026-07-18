package com.ejemplo.app.infraestructure.sagas.persistencia;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DatosNegocioJpaRepository extends JpaRepository<DatosNegocioEntity, UUID> {
    Optional<DatosNegocioEntity> findByExternalId(String externalId);

    // Huérfano: ningún proceso (de las 4 sagas de la tramitación) comparte ya su external_id
    // -- ocurre cuando la Fase 3 purgó todas las órdenes de la tramitación (incluida
    // proceso_saga_principal, que referenciaba datosnegocio_id).
    // Devuelve el id en crudo (String, mismo tipo de columna que datosnegocio_id): la
    // conversión a UUID/DatosNegocioId es cosa del adaptador, no de esta query nativa.
    @Query(value = "SELECT d.datosnegocio_id FROM datos_negocio d "
            + "WHERE NOT EXISTS (SELECT 1 FROM proceso p WHERE p.external_id = d.external_id)",
            nativeQuery = true)
    List<String> idsHuerfanos();
}
