package com.ejemplo.app.infraestructure.sagas.persistencia;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DatosNegocioJpaRepository extends JpaRepository<DatosNegocioEntity, UUID> {
    Optional<DatosNegocioEntity> findByExternalId(String externalId);

    // Huérfano: ninguna orden (de las 4 sagas de la tramitación) comparte ya su external_id
    // -- ocurre cuando la Fase 3 purgó todas las órdenes de la tramitación (incluida
    // proceso_saga_principal, que referenciaba datosnegocio_id). external_id vivía en la
    // tabla proceso; tras la fusión de orden+proceso en una sola fila (ver db/orden.sql),
    // vive en orden, de ahí el JOIN contra orden y no contra proceso.
    // Devuelve el id en crudo (String, mismo tipo de columna que datosnegocio_id): la
    // conversión a UUID/DatosNegocioId es cosa del adaptador, no de esta query nativa.
    @Query(value = "SELECT d.datosnegocio_id FROM datos_negocio d "
            + "WHERE NOT EXISTS (SELECT 1 FROM orden o WHERE o.external_id = d.external_id)",
            nativeQuery = true)
    List<String> idsHuerfanos();
}
