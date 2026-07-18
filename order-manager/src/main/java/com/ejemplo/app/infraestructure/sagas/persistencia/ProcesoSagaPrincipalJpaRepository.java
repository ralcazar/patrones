package com.ejemplo.app.infraestructure.sagas.persistencia;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcesoSagaPrincipalJpaRepository extends JpaRepository<ProcesoSagaPrincipalEntity, UUID> {

    // clearAutomatically: mismo motivo que ProcesoJpaRepository.borrarPorIds.
    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM proceso_saga_principal WHERE orden_id IN :ids", nativeQuery = true)
    void borrarPorIds(@Param("ids") List<UUID> ids);
}
