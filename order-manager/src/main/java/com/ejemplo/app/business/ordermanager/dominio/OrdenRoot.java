package com.ejemplo.app.business.ordermanager.dominio;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

/**
 * El ÚNICO agregado por orden: raíz que contiene el {@link Proceso} (estado de
 * NEGOCIO) y añade el estado de EJECUCIÓN (reintentos, lease del token,
 * ticket, instante de finalización) Y sus propias marcas temporales
 * ({@code creadaEn}/{@code actualizadaEn}). Una sola {@code version} protege
 * el agregado completo: varios flujos mutan negocio y ejecución en la misma
 * transacción, así que separarlos en dos agregados violaría la regla de un
 * agregado por transacción.
 *
 * Reloj determinista: nunca llama a {@code Instant.now()}; todo método que
 * depende del tiempo recibe {@code Instant ahora} como último parámetro, que
 * aporta la capa de aplicación. {@code actualizadaEn} la fija el propio
 * agregado en cada mutación con ese {@code ahora}, no un {@code @PreUpdate}
 * de infraestructura.
 */
@AggregateRoot
public final class OrdenRoot {

    @Identity
    private final OrdenId id;
    private Proceso<?> proceso;
    private final Prioridad prioridad;
    private int intentos;
    private Instant proximoReintentoEn;
    private UUID tokenTrabajador;
    private Instant tokenExpiraEn;
    private Instant ticketAbiertoEn;
    private Instant completadaEn;
    private DetalleError ultimoError;
    private final long version;
    private final Instant creadaEn;
    private Instant actualizadaEn;

    private OrdenRoot(OrdenId id, Proceso<?> proceso, Prioridad prioridad, int intentos, Instant proximoReintentoEn,
            UUID tokenTrabajador, Instant tokenExpiraEn, Instant ticketAbiertoEn,
            Instant completadaEn, DetalleError ultimoError, long version,
            Instant creadaEn, Instant actualizadaEn) {
        this.id = id;
        this.proceso = proceso;
        this.prioridad = prioridad;
        this.intentos = intentos;
        this.proximoReintentoEn = proximoReintentoEn;
        this.tokenTrabajador = tokenTrabajador;
        this.tokenExpiraEn = tokenExpiraEn;
        this.ticketAbiertoEn = ticketAbiertoEn;
        this.completadaEn = completadaEn;
        this.ultimoError = ultimoError;
        this.version = version;
        this.creadaEn = creadaEn;
        this.actualizadaEn = actualizadaEn;
    }

    /** Alta con prioridad explícita (la fija quien crea la orden, p. ej. una saga). */
    public static OrdenRoot nueva(Proceso<?> proceso, Prioridad prioridad, Instant ahora) {
        return new OrdenRoot(proceso.id(), proceso, prioridad, 0, ahora, null, null, null, null, null, 0L,
                ahora, ahora);
    }

    /** Alta con prioridad {@link Prioridad#normal()} (conveniencia para quien no distingue prioridad). */
    public static OrdenRoot nueva(Proceso<?> proceso, Instant ahora) {
        return nueva(proceso, Prioridad.normal(), ahora);
    }

    /** Para el adaptador de persistencia. */
    public static OrdenRoot rehidratar(Proceso<?> proceso, Prioridad prioridad, int intentos, Instant proximoReintentoEn,
            UUID tokenTrabajador, Instant tokenExpiraEn, Instant ticketAbiertoEn,
            Instant completadaEn, DetalleError ultimoError, long version,
            Instant creadaEn, Instant actualizadaEn) {
        return new OrdenRoot(proceso.id(), proceso, prioridad, intentos, proximoReintentoEn, tokenTrabajador,
                tokenExpiraEn, ticketAbiertoEn, completadaEn, ultimoError, version, creadaEn, actualizadaEn);
    }

    /**
     * Sobrecarga de conveniencia con {@link Prioridad#normal()} (evita romper llamantes que no distinguen
     * prioridad). Por defecto {@code creadaEn = actualizadaEn = proximoReintentoEn}: quien necesite marcas
     * temporales reales debe usar la forma completa.
     */
    public static OrdenRoot rehidratar(Proceso<?> proceso, int intentos, Instant proximoReintentoEn,
            UUID tokenTrabajador, Instant tokenExpiraEn, Instant ticketAbiertoEn,
            Instant completadaEn, DetalleError ultimoError, long version) {
        return rehidratar(proceso, Prioridad.normal(), intentos, proximoReintentoEn, tokenTrabajador,
                tokenExpiraEn, ticketAbiertoEn, completadaEn, ultimoError, version,
                proximoReintentoEn, proximoReintentoEn);
    }

    /**
     * Sobrecarga de conveniencia con prioridad explícita pero sin marcas temporales (evita romper llamantes
     * que no las distinguen). Por defecto {@code creadaEn = actualizadaEn = proximoReintentoEn}: quien
     * necesite marcas temporales reales debe usar la forma completa.
     */
    public static OrdenRoot rehidratar(Proceso<?> proceso, Prioridad prioridad, int intentos, Instant proximoReintentoEn,
            UUID tokenTrabajador, Instant tokenExpiraEn, Instant ticketAbiertoEn,
            Instant completadaEn, DetalleError ultimoError, long version) {
        return rehidratar(proceso, prioridad, intentos, proximoReintentoEn, tokenTrabajador,
                tokenExpiraEn, ticketAbiertoEn, completadaEn, ultimoError, version,
                proximoReintentoEn, proximoReintentoEn);
    }

    /**
     * Gana el reclamo de ejecución: se le asigna un token de trabajador con
     * un lease que expira en {@code ahora + lease}. Mientras el lease esté
     * vigente, ningún otro worker puede reclamar esta orden (ver
     * {@link #tieneTokenVigente}); si el proceso que lo tiene muere sin
     * liberarlo, el lease vence solo y la orden vuelve a ser reclamable.
     */
    public void asignarToken(UUID token, Duration lease, Instant ahora) {
        this.tokenTrabajador = token;
        this.tokenExpiraEn = ahora.plus(lease);
        this.actualizadaEn = ahora;
    }

    /** En la tx de cada paso completado: evita que el lease venza a media orden larga. */
    public void renovarLease(Duration lease, Instant ahora) {
        this.tokenExpiraEn = ahora.plus(lease);
        this.actualizadaEn = ahora;
    }

    /** Hay un worker con el token en curso y su lease todavía no ha vencido. */
    public boolean tieneTokenVigente(Instant ahora) {
        return tokenTrabajador != null && tokenExpiraEn != null && tokenExpiraEn.isAfter(ahora);
    }

    /** Deja la orden reclamable de nuevo, sin dueño de ejecución. */
    public void liberarToken() {
        this.tokenTrabajador = null;
        this.tokenExpiraEn = null;
    }

    /** Paso OK: la orden vuelve a estar "sana". Un atasco posterior abrirá un ticket NUEVO. */
    public void resetearIntentos(Instant ahora) {
        this.intentos = 0;
        this.ticketAbiertoEn = null;
        this.ultimoError = null;
        this.actualizadaEn = ahora;
    }

    /** Escalera de reintentos consumida (ver {@link PoliticaReintentos}): soporte ya fue avisado. */
    public void marcarTicketAbierto(Instant ahora) {
        this.ticketAbiertoEn = ahora;
        this.actualizadaEn = ahora;
    }

    /** Eventos externos o intervención de soporte: la orden es candidata inmediata. */
    public void despertar(Instant ahora) {
        this.proximoReintentoEn = ahora;
        liberarToken();
        this.actualizadaEn = ahora;
    }

    /** Secundaria2 esperando el evento Kafka de respuesta. */
    public void aparcar(Duration ventana, Instant ahora) {
        this.proximoReintentoEn = ahora.plus(ventana);
        liberarToken();
        this.actualizadaEn = ahora;
    }

    /** Un paso ha fallado: cuenta el intento, calcula la próxima ventana con la política y libera el token. */
    public void programarReintento(PoliticaReintentos politica, DetalleError error, Instant ahora) {
        this.intentos++;
        this.proximoReintentoEn = ahora.plus(politica.esperaTras(intentos));
        this.ultimoError = error;
        liberarToken();
        this.actualizadaEn = ahora;
    }

    /**
     * La FSM de negocio llegó a un estado final (ver {@link Proceso#terminada}):
     * no vuelve a ser candidata. Limpia intentos y último error igual que
     * {@link #resetearIntentos}: una orden terminada en OK no debe quedar en
     * BBDD con rastro de un fallo ya superado (a diferencia de {@link #aparcar},
     * que sí lo conserva mientras la orden siga viva).
     */
    public void finalizar(Instant ahora) {
        resetearIntentos(ahora);
        this.completadaEn = ahora;
        liberarToken();
        this.actualizadaEn = ahora;
    }

    /** Todavía no ha finalizado: sigue siendo candidata a ejecución. */
    public boolean estaViva() {
        return completadaEn == null;
    }

    /**
     * ¿Ya llegó el turno de ejecución de esta orden? Espejo EXACTO del
     * predicado {@code o.proximo_reintento_en <= :ahora} de la query
     * {@code buscarCandidatas} en {@code OrdenJpaRepository}
     * (infraestructure.ordermanager.persistencia) -- y de su copia en
     * {@code RepositorioOrdenEnMemoria.buscarEjecutables} para los tests.
     * Los tres deben mantenerse alineados: si cambia uno, cambian los otros.
     * Lo usa {@code ServicioContinuarOrden.reclamarToken} para re-comprobar,
     * sobre la fila recién cargada, que la orden sigue en turno (otro actor
     * pudo haberla aparcado entre el barrido de candidatas y el reclamo).
     */
    public boolean turnoVencido(Instant ahora) {
        return !proximoReintentoEn.isAfter(ahora);
    }

    /**
     * Sustituye el valor del {@link Proceso} tras una transición de negocio.
     * Al ser {@code Proceso} un value object inmutable (ver su javadoc), toda
     * transición devuelve una instancia nueva en vez de mutar la existente;
     * la raíz del agregado es la DUEÑA del valor y el ÚNICO punto donde se
     * reasigna el campo — nadie más (aplicación, infraestructura) debe
     * reemplazarlo por otra vía.
     */
    public void reemplazarProceso(Proceso<?> nuevo, Instant ahora) {
        this.proceso = nuevo;
        this.actualizadaEn = ahora;
    }

    public Proceso<?> proceso() { return proceso; }
    public TipoOrden tipo() { return proceso.tipo(); }
    public Prioridad prioridad() { return prioridad; }

    public OrdenId id() { return id; }
    public int intentos() { return intentos; }
    public Instant proximoReintentoEn() { return proximoReintentoEn; }
    public UUID tokenTrabajador() { return tokenTrabajador; }
    public Instant tokenExpiraEn() { return tokenExpiraEn; }
    public Instant ticketAbiertoEn() { return ticketAbiertoEn; }
    public Instant completadaEn() { return completadaEn; }
    public DetalleError ultimoError() { return ultimoError; }
    public long version() { return version; }
    public Instant creadaEn() { return creadaEn; }
    public Instant actualizadaEn() { return actualizadaEn; }
}
