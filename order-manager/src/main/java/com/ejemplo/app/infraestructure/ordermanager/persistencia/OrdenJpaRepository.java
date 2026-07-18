package com.ejemplo.app.infraestructure.ordermanager.persistencia;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrdenJpaRepository extends JpaRepository<OrdenEntity, String> {

    String RESUMEN_SELECT = """
            SELECT o.orden_id AS ordenId, p.tipo AS tipo, p.external_id AS externalId, p.estado AS estado,
                   o.intentos AS intentos, o.ticket_abierto_en AS ticketAbiertoEn,
                   o.proximo_reintento_en AS proximoReintentoEn, o.creada_en AS iniciadaEn,
                   o.actualizada_en AS actualizadaEn
            FROM orden o JOIN proceso p ON p.orden_id = o.orden_id
            """;

    /**
     * Candidatas del planificador: vencido el reintento, viva, y sin token
     * vigente (nunca asignado o con lease caducado). Oracle no tiene índices
     * parciales, de ahí el índice funcional sobre esta misma expresión.
     */
    @Query(value = """
            SELECT o.orden_id AS ordenId, p.tipo AS tipo
            FROM orden o JOIN proceso p ON p.orden_id = o.orden_id
            WHERE o.proximo_reintento_en <= :ahora
              AND o.resultado IS NULL
              AND (o.token_trabajador IS NULL OR o.token_expira_en <= :ahora)
            ORDER BY o.proximo_reintento_en
            FETCH FIRST :limite ROWS ONLY
            """, nativeQuery = true)
    List<CandidataFila> buscarCandidatas(@Param("ahora") Instant ahora, @Param("limite") int limite);

    /**
     * ¿Existe alguna candidata? Mismo predicado que {@link #buscarCandidatas}
     * (¡mantenerlos idénticos, comparten el índice funcional!), sin traer
     * filas: {@code ROWNUM = 1} corta en la primera coincidencia (devuelve 0 o 1).
     */
    @Query(value = """
            SELECT COUNT(*) FROM orden o
            WHERE o.proximo_reintento_en <= :ahora
              AND o.resultado IS NULL
              AND (o.token_trabajador IS NULL OR o.token_expira_en <= :ahora)
              AND ROWNUM = 1
            """, nativeQuery = true)
    int existeCandidata(@Param("ahora") Instant ahora);

    @Query(value = """
            SELECT orden_id FROM orden WHERE resultado IS NOT NULL AND actualizada_en < :corte
            """, nativeQuery = true)
    List<String> idsFinalizadasAntesDe(@Param("corte") Instant corte);

    // clearAutomatically: ver el mismo comentario en ProcesoJpaRepository.borrarPorIds.
    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM orden WHERE orden_id IN :ids", nativeQuery = true)
    void borrarPorIds(@Param("ids") List<String> ids);

    /** Escalera de reintentos consumida: candidata a bandeja de trabajo y a ticket. */
    @Query(value = """
            SELECT o.orden_id AS ordenId, p.tipo AS tipo, p.external_id AS externalId, o.intentos AS intentos
            FROM orden o JOIN proceso p ON p.orden_id = o.orden_id
            WHERE o.intentos >= 8 AND o.ticket_abierto_en IS NULL AND o.resultado IS NULL
            """, nativeQuery = true)
    List<TicketPendienteFila> buscarTicketsPendientes();

    @Query(value = RESUMEN_SELECT + "WHERE o.intentos >= 8", nativeQuery = true)
    List<OrdenResumenFila> ordenesBloqueadas();

    @Query(value = RESUMEN_SELECT
            + "WHERE o.token_trabajador IS NOT NULL AND o.token_expira_en > :ahora AND o.resultado IS NULL",
            nativeQuery = true)
    List<OrdenResumenFila> ordenesEnEjecucion(@Param("ahora") Instant ahora);

    @Query(value = RESUMEN_SELECT
            + "WHERE o.intentos >= 8 AND o.ticket_abierto_en IS NULL AND o.resultado IS NULL",
            nativeQuery = true)
    List<OrdenResumenFila> ordenesConTicketPendiente();

    @Query(value = RESUMEN_SELECT + """
            WHERE (:estado IS NULL OR p.estado = :estado)
              AND (:iniciadaDesde IS NULL OR o.creada_en >= :iniciadaDesde)
              AND (:iniciadaHasta IS NULL OR o.creada_en <= :iniciadaHasta)
              AND (:actualizadaDesde IS NULL OR o.actualizada_en >= :actualizadaDesde)
              AND (:actualizadaHasta IS NULL OR o.actualizada_en <= :actualizadaHasta)
            """, nativeQuery = true)
    List<OrdenResumenFila> buscar(@Param("estado") String estado,
            @Param("iniciadaDesde") Instant iniciadaDesde, @Param("iniciadaHasta") Instant iniciadaHasta,
            @Param("actualizadaDesde") Instant actualizadaDesde, @Param("actualizadaHasta") Instant actualizadaHasta);

    @Query(value = RESUMEN_SELECT + "WHERE p.external_id = :externalId", nativeQuery = true)
    List<OrdenResumenFila> porExternalId(@Param("externalId") String externalId);

    @Query(value = RESUMEN_SELECT + "WHERE p.tipo = :tipo AND o.orden_id = :ordenId", nativeQuery = true)
    Optional<OrdenResumenFila> resumenDe(@Param("tipo") String tipo, @Param("ordenId") String ordenId);
}
