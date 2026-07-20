package com.ejemplo.app.infraestructure.sagas.datosnegocio.persistencia;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Único adaptador de escritura/lectura JPA del agregado {@code datos_negocio} (ver AdaptadorDatosNegocio). */
public interface DatosNegocioJpaRepository extends JpaRepository<DatosNegocioEntity, UUID> {
    Optional<DatosNegocioEntity> findByExternalId(String externalId);

    // Huérfano: ninguna fila de la tabla orden (de las 4 sagas de la tramitación:
    // principal + 3 secundarias) comparte ya su external_id -- ocurre cuando
    // ServicioLimpiezaDatos ya purgó todas las órdenes de la tramitación. Devuelve el
    // id en crudo (String, mismo tipo de columna que datosnegocio_id): la conversión a
    // UUID/DatosNegocioId es cosa del adaptador, no de esta query nativa.
    @Query(value = "SELECT d.datosnegocio_id FROM datos_negocio d "
            + "WHERE NOT EXISTS (SELECT 1 FROM orden o WHERE o.external_id = d.external_id)",
            nativeQuery = true)
    List<String> idsHuerfanos();
}
