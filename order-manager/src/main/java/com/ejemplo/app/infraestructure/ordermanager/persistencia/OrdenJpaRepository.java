package com.ejemplo.app.infraestructure.ordermanager.persistencia;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrdenJpaRepository extends JpaRepository<OrdenEntity, UUID> {

    String RESUMEN_SELECT = """
            SELECT o.orden_id AS ordenId, o.tipo AS tipo, o.external_id AS externalId, o.estado AS estado,
                   o.intentos AS intentos, o.ticket_abierto_en AS ticketAbiertoEn,
                   o.proximo_reintento_en AS proximoReintentoEn, o.creada_en AS iniciadaEn,
                   o.actualizada_en AS actualizadaEn,
                   o.ultimo_error_tipo AS ultimoErrorTipo, o.ultimo_error_mensaje AS ultimoErrorMensaje
            FROM orden o
            """;

    /** Idempotencia de POST /tramitaciones: localizar la orden principal ya creada para un externalId. */
    Optional<OrdenEntity> findByExternalIdAndTipo(String externalId, String tipo);

    /**
     * Candidatas del planificador: vencido el reintento, viva, y sin token
     * vigente (nunca asignado o con lease caducado). Oracle no tiene índices
     * parciales, de ahí el índice funcional sobre esta misma expresión.
     */
    @Query(value = """
            SELECT o.orden_id AS ordenId, o.tipo AS tipo
            FROM orden o
            WHERE o.proximo_reintento_en <= :ahora
              AND o.completada_en IS NULL
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
              AND o.completada_en IS NULL
              AND (o.token_trabajador IS NULL OR o.token_expira_en <= :ahora)
              AND ROWNUM = 1
            """, nativeQuery = true)
    int existeCandidata(@Param("ahora") Instant ahora);

    @Query(value = """
            SELECT orden_id FROM orden WHERE completada_en IS NOT NULL AND actualizada_en < :corte
            """, nativeQuery = true)
    List<UUID> idsFinalizadasAntesDe(@Param("corte") Instant corte);

    /**
     * Purga por tramitación (grupo de órdenes que comparten external_id):
     * solo grupos SIN ninguna orden viva (el COUNT del CASE cuenta las
     * completada_en IS NULL del grupo; HAVING = 0 exige que no haya ninguna)
     * y cuya última en terminar sea anterior al corte.
     */
    @Query(value = """
            SELECT external_id FROM orden
            GROUP BY external_id
            HAVING COUNT(CASE WHEN completada_en IS NULL THEN 1 END) = 0
               AND MAX(completada_en) < :corte
            """, nativeQuery = true)
    List<String> externalIdsFinalizadosAntesDe(@Param("corte") Instant corte);

    /** Ids de todas las órdenes (de cualquier tipo) de los external_ids indicados. */
    @Query(value = "SELECT orden_id FROM orden WHERE external_id IN :externalIds", nativeQuery = true)
    List<UUID> idsPorExternalIds(@Param("externalIds") List<String> externalIds);

    // clearAutomatically: sin esto, una entidad ya cargada en el contexto de persistencia
    // (p. ej. por un merge/save previo en la misma transacción) seguiría "viva" en el
    // cache de 1er nivel tras el DELETE nativo, y un find() posterior la devolvería
    // fantasma en vez de reflejar el borrado real en BD.
    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM orden WHERE orden_id IN :ids", nativeQuery = true)
    void borrarPorIds(@Param("ids") List<UUID> ids);

    // clearAutomatically: ver el mismo comentario en borrarPorIds. Sin ON DELETE CASCADE
    // (prohibido, ver CLAUDE.md) el borrado de la hija proceso_auditoria es explícito, y
    // tiene que ocurrir ANTES del borrado del padre (orden) en purgarFinalizadasAntesDe.
    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM proceso_auditoria WHERE orden_id IN :ids", nativeQuery = true)
    void borrarAuditoriaPorIds(@Param("ids") List<UUID> ids);

    /** Escalera de reintentos consumida: candidata a bandeja de trabajo y a ticket. */
    @Query(value = """
            SELECT o.orden_id AS ordenId, o.tipo AS tipo, o.external_id AS externalId, o.intentos AS intentos,
                   o.ultimo_error_tipo AS ultimoErrorTipo, o.ultimo_error_mensaje AS ultimoErrorMensaje
            FROM orden o
            WHERE o.intentos >= 8 AND o.ticket_abierto_en IS NULL AND o.completada_en IS NULL
            """, nativeQuery = true)
    List<TicketPendienteFila> buscarTicketsPendientes();

    @Query(value = RESUMEN_SELECT + "WHERE o.intentos >= 8", nativeQuery = true)
    List<OrdenResumenFila> ordenesBloqueadas();

    @Query(value = RESUMEN_SELECT
            + "WHERE o.token_trabajador IS NOT NULL AND o.token_expira_en > :ahora AND o.completada_en IS NULL",
            nativeQuery = true)
    List<OrdenResumenFila> ordenesEnEjecucion(@Param("ahora") Instant ahora);

    @Query(value = RESUMEN_SELECT
            + "WHERE o.intentos >= 8 AND o.ticket_abierto_en IS NULL AND o.completada_en IS NULL",
            nativeQuery = true)
    List<OrdenResumenFila> ordenesConTicketPendiente();

    @Query(value = RESUMEN_SELECT + """
            WHERE (:estado IS NULL OR o.estado = :estado)
              AND (:iniciadaDesde IS NULL OR o.creada_en >= :iniciadaDesde)
              AND (:iniciadaHasta IS NULL OR o.creada_en <= :iniciadaHasta)
              AND (:actualizadaDesde IS NULL OR o.actualizada_en >= :actualizadaDesde)
              AND (:actualizadaHasta IS NULL OR o.actualizada_en <= :actualizadaHasta)
            """, nativeQuery = true)
    List<OrdenResumenFila> buscar(@Param("estado") String estado,
            @Param("iniciadaDesde") Instant iniciadaDesde, @Param("iniciadaHasta") Instant iniciadaHasta,
            @Param("actualizadaDesde") Instant actualizadaDesde, @Param("actualizadaHasta") Instant actualizadaHasta);

    @Query(value = RESUMEN_SELECT + "WHERE o.external_id = :externalId", nativeQuery = true)
    List<OrdenResumenFila> porExternalId(@Param("externalId") String externalId);

    @Query(value = RESUMEN_SELECT + "WHERE o.tipo = :tipo AND o.orden_id = :ordenId", nativeQuery = true)
    Optional<OrdenResumenFila> resumenDe(@Param("tipo") String tipo, @Param("ordenId") UUID ordenId);
}
