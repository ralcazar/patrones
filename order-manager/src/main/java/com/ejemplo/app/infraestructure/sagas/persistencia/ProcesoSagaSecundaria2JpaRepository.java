package com.ejemplo.app.infraestructure.sagas.persistencia;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tabla satélite (SPI {@link com.ejemplo.app.infraestructure.ordermanager.persistencia.MapeadorProceso}) del contexto de la saga secundaria 2. */
public interface ProcesoSagaSecundaria2JpaRepository extends JpaRepository<ProcesoSagaSecundaria2Entity, UUID> {

    // clearAutomatically: sin esto, una entidad ya cargada en el contexto de
    // persistencia seguiría "viva" en el cache de 1er nivel tras el DELETE
    // nativo, y un find() posterior la devolvería fantasma en vez de reflejar
    // el borrado real en BD (mismo motivo que OrdenJpaRepository.borrarPorIds).
    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM proceso_saga_secundaria2 WHERE orden_id IN :ids", nativeQuery = true)
    void borrarPorIds(@Param("ids") List<UUID> ids);
}
