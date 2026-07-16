package com.ejemplo.app.infraestructure.ordermanager.saga;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SagaJpaRepository extends JpaRepository<SagaEntity, String> {

    @Modifying
    @Query(value = "DELETE FROM saga WHERE saga_id IN :ids", nativeQuery = true)
    void borrarPorIds(@Param("ids") List<String> ids);
}
