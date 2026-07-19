package com.ejemplo.app.carga.analisis;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Consultas JDBC directas (sin JPA: el analizador es harness, no producción)
 * sobre la H2 en fichero de una ejecución (misma URL que usa
 * {@code LanzadorPruebaCarga.contarOrdenesVivas}). Esquema real en
 * {@code order-manager/db/orden.sql} (negocio + ejecución fusionados en una
 * sola tabla desde la fase 2 del refactor).
 */
final class RepositorioAnalisisBbdd implements AutoCloseable {

    private final Connection conexion;

    RepositorioAnalisisBbdd(String urlJdbc) {
        try {
            this.conexion = DriverManager.getConnection(urlJdbc, "sa", "");
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo conectar a " + urlJdbc, e);
        }
    }

    record FilaOrdenViva(String ordenId, String tipo, int intentos, String proximoReintentoEn) {}

    record FilaDistribucionEstado(String tipo, String estado, long total, long completadas) {}

    /**
     * Candidatas a "estancada": viva, sin ticket, con el turno vencido ANTES
     * de {@code vencidasAntesDe} (invariante 1). El límite lo pone el llamante
     * (ver {@code AnalizadorEjecucion}): el análisis corre post-mortem, con
     * los pods ya cerrados, así que comparar contra el "ahora" del analizador
     * marcaría como estancada cualquier orden cuyo turno venciera en los
     * últimos instantes de vida de los pods, sin barrido restante que pudiera
     * recogerla.
     */
    List<FilaOrdenViva> ordenesEstancadas(Instant vencidasAntesDe) {
        String sql = """
                SELECT o.orden_id, o.tipo, o.intentos, o.proximo_reintento_en
                FROM orden o
                WHERE o.completada_en IS NULL
                  AND o.ticket_abierto_en IS NULL
                  AND o.proximo_reintento_en <= ?
                ORDER BY o.proximo_reintento_en
                """;
        List<FilaOrdenViva> filas = new ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setTimestamp(1, Timestamp.from(vencidasAntesDe));
            try (ResultSet rs = sentencia.executeQuery()) {
                while (rs.next()) {
                    filas.add(mapearFilaOrdenViva(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Fallo consultando la BBDD del harness", e);
        }
        return filas;
    }

    /** Ticket abierto sin haber agotado la escalera de reintentos (invariante 4, dirección "de más"). */
    List<FilaOrdenViva> ordenesTicketSinMotivo() {
        return consultarOrdenesVivas("""
                SELECT o.orden_id, o.tipo, o.intentos, o.proximo_reintento_en
                FROM orden o
                WHERE o.completada_en IS NULL
                  AND o.ticket_abierto_en IS NOT NULL
                  AND o.intentos < 8
                """);
    }

    /** Escalera agotada (intentos >= 8) sin ticket abierto (invariante 4, dirección "de menos"). */
    List<FilaOrdenViva> ordenesReintentosAgotadosSinTicket() {
        return consultarOrdenesVivas("""
                SELECT o.orden_id, o.tipo, o.intentos, o.proximo_reintento_en
                FROM orden o
                WHERE o.completada_en IS NULL
                  AND o.ticket_abierto_en IS NULL
                  AND o.intentos >= 8
                """);
    }

    private List<FilaOrdenViva> consultarOrdenesVivas(String sql) {
        List<FilaOrdenViva> filas = new ArrayList<>();
        try (Statement sentencia = conexion.createStatement();
                ResultSet rs = sentencia.executeQuery(sql)) {
            while (rs.next()) {
                filas.add(mapearFilaOrdenViva(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Fallo consultando la BBDD del harness", e);
        }
        return filas;
    }

    private static FilaOrdenViva mapearFilaOrdenViva(ResultSet rs) throws SQLException {
        return new FilaOrdenViva(rs.getString(1), rs.getString(2), rs.getInt(3),
                String.valueOf(rs.getTimestamp(4)));
    }

    long contarTotalOrdenes() {
        return contarUna("SELECT COUNT(*) FROM orden");
    }

    long contarCompletadas() {
        return contarUna("SELECT COUNT(*) FROM orden WHERE completada_en IS NOT NULL");
    }

    long contarTicketsAbiertos() {
        return contarUna("SELECT COUNT(*) FROM orden WHERE ticket_abierto_en IS NOT NULL");
    }

    long contarVivas() {
        return contarUna("SELECT COUNT(*) FROM orden WHERE completada_en IS NULL");
    }

    private long contarUna(String sql) {
        try (Statement sentencia = conexion.createStatement();
                ResultSet rs = sentencia.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException("Fallo consultando la BBDD del harness", e);
        }
    }

    /** Distribución final de estados: por tipo de orden + estado de negocio (ambos en la misma tabla orden). */
    List<FilaDistribucionEstado> distribucionEstados() {
        List<FilaDistribucionEstado> filas = new ArrayList<>();
        String sql = """
                SELECT o.tipo, o.estado, COUNT(*) AS total,
                       SUM(CASE WHEN o.completada_en IS NOT NULL THEN 1 ELSE 0 END) AS completadas
                FROM orden o
                GROUP BY o.tipo, o.estado
                ORDER BY o.tipo, o.estado
                """;
        try (Statement sentencia = conexion.createStatement();
                ResultSet rs = sentencia.executeQuery(sql)) {
            while (rs.next()) {
                filas.add(new FilaDistribucionEstado(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Fallo consultando la BBDD del harness", e);
        }
        return filas;
    }

    @Override
    public void close() {
        try {
            conexion.close();
        } catch (SQLException e) {
            throw new IllegalStateException("Fallo cerrando la conexión de análisis", e);
        }
    }
}
