package com.ejemplo.app.infraestructure.ordermanager.persistencia;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcesoJpaRepository extends JpaRepository<ProcesoEntity, UUID> {

    // clearAutomatically: sin esto, una entidad ya cargada en el contexto de persistencia
    // (p. ej. por un merge/save previo en la misma transacción) seguiría "viva" en el
    // cache de 1er nivel tras el DELETE nativo, y un find() posterior la devolvería
    // fantasma en vez de reflejar el borrado real en BD.
    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM proceso WHERE orden_id IN :ids", nativeQuery = true)
    void borrarPorIds(@Param("ids") List<UUID> ids);
}
