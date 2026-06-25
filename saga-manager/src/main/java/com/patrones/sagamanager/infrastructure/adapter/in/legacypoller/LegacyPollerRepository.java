package com.patrones.sagamanager.infrastructure.adapter.in.legacypoller;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Acceso JDBC dedicado a la tabla legacy. No usa el agregado de dominio: claim atómico sin
 * bloqueo pesimista (patrón lease). Seguro multi-instancia: la condición WHERE del UPDATE se
 * revalida en cada intento, así que solo una instancia puede ganar el claim de una fila dada.
 */
@Repository
public class LegacyPollerRepository {

	private static final String PENDING_STATUS = "PDTE-PROCESAR";
	private static final String PROCESSING_STATUS = "PROCESANDO";

	private final JdbcTemplate jdbcTemplate;

	public LegacyPollerRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<Long> findClaimableIds(int pageSize, Instant leaseThreshold) {
		return jdbcTemplate.queryForList(
				"SELECT id FROM legacy_request "
						+ "WHERE estado = ? OR (estado = ? AND fecha_cogida < ?) "
						+ "ORDER BY id LIMIT ?",
				Long.class,
				PENDING_STATUS, PROCESSING_STATUS, leaseThreshold, pageSize);
	}

	/**
	 * Intenta reclamar una fila. Devuelve true solo si esta instancia ganó el claim (1 fila
	 * afectada); false si otro worker ya la cogió (0 filas afectadas).
	 */
	public boolean claim(long id, Instant leaseThreshold, Instant now) {
		int updated = jdbcTemplate.update(
				"UPDATE legacy_request SET estado = ?, fecha_cogida = ? "
						+ "WHERE id = ? AND (estado = ? OR (estado = ? AND fecha_cogida < ?))",
				PROCESSING_STATUS, now, id, PENDING_STATUS, PROCESSING_STATUS, leaseThreshold);
		return updated == 1;
	}

	public Optional<LegacyRequestRow> findById(long id) {
		List<LegacyRequestRow> rows = jdbcTemplate.query(
				"SELECT id, id_borrador, payload FROM legacy_request WHERE id = ?",
				(rs, rowNum) -> new LegacyRequestRow(rs.getLong("id"), rs.getString("id_borrador"), rs.getString("payload")),
				id);
		return rows.stream().findFirst();
	}

	public void markFinal(long id, String finalStatus) {
		jdbcTemplate.update("UPDATE legacy_request SET estado = ? WHERE id = ?", finalStatus, id);
	}
}
