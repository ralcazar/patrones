package com.ejemplo.app.infraestructure.ordermanager.cola;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


/**
 * Patrón lease con UPDATE condicional, con UN matiz por la fusión con las
 * sagas: una orden solo es elegible cuando ejecutar_desde <= now. Así los
 * reintentos con backoff y los timeouts son simples órdenes diferidas y
 * desaparece la necesidad de un scheduler externo (Quartz/db-scheduler).
 */
public interface RepositorioOrdenes extends JpaRepository<Orden, Long> {

    @Query(value = """
            SELECT id FROM ordenes
            WHERE ejecutar_desde <= :ahora
              AND (estado = 'PENDIENTE'
                   OR (estado = 'EN_PROCESO' AND reclamada_en < :limiteLease))
            ORDER BY ejecutar_desde, creada_en
            LIMIT :ventana
            """, nativeQuery = true)
    List<Long> buscarCandidatas(@Param("ahora") Instant ahora,
                                @Param("limiteLease") Instant limiteLease,
                                @Param("ventana") int ventana);

    @Modifying
    @Query(value = """
            UPDATE ordenes
            SET estado = 'EN_PROCESO', reclamada_en = :ahora, token = :token
            WHERE id = :id
              AND ejecutar_desde <= :ahora
              AND (estado = 'PENDIENTE'
                   OR (estado = 'EN_PROCESO' AND reclamada_en < :limiteLease))
            """, nativeQuery = true)
    int reclamar(@Param("id") Long id,
                 @Param("token") String token,
                 @Param("ahora") Instant ahora,
                 @Param("limiteLease") Instant limiteLease);

    @Modifying
    @Query(value = """
            UPDATE ordenes SET estado = :estado
            WHERE id = :id AND token = :token AND estado = 'EN_PROCESO'
            """, nativeQuery = true)
    int finalizar(@Param("id") Long id,
                  @Param("estado") String estado,
                  @Param("token") String token);

    @Modifying
    @Query(value = """
            DELETE FROM ordenes
            WHERE estado = 'COMPLETADA' AND creada_en < :corte
            """, nativeQuery = true)
    long purgarCompletadasAntesDe(@Param("corte") Instant corte);
}
