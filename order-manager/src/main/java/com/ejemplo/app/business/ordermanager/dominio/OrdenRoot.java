package com.ejemplo.app.business.ordermanager.dominio;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

/**
 * El ÚNICO agregado por orden: raíz que contiene el {@link Proceso} (estado de
 * NEGOCIO) y añade el estado de EJECUCIÓN (reintentos, lease del token,
 * ticket, resultado final). Una sola {@code version} protege el agregado
 * completo: varios flujos mutan negocio y ejecución en la misma transacción,
 * así que separarlos en dos agregados violaría la regla de un agregado por
 * transacción.
 *
 * Reloj determinista: nunca llama a {@code Instant.now()}; todo método que
 * depende del tiempo recibe {@code Instant ahora} como último parámetro, que
 * aporta la capa de aplicación.
 */
@AggregateRoot
public final class OrdenRoot {

    @Identity
    private final OrdenId id;
    private final Proceso<?> proceso;
    private int intentos;
    private Instant proximoReintentoEn;
    private UUID tokenTrabajador;
    private Instant tokenExpiraEn;
    private Instant ticketAbiertoEn;
    private ResultadoOrden resultado;
    private final long version;

    private OrdenRoot(OrdenId id, Proceso<?> proceso, int intentos, Instant proximoReintentoEn,
            UUID tokenTrabajador, Instant tokenExpiraEn, Instant ticketAbiertoEn,
            ResultadoOrden resultado, long version) {
        this.id = id;
        this.proceso = proceso;
        this.intentos = intentos;
        this.proximoReintentoEn = proximoReintentoEn;
        this.tokenTrabajador = tokenTrabajador;
        this.tokenExpiraEn = tokenExpiraEn;
        this.ticketAbiertoEn = ticketAbiertoEn;
        this.resultado = resultado;
        this.version = version;
    }

    public static OrdenRoot nueva(Proceso<?> proceso, Instant ahora) {
        return new OrdenRoot(proceso.id(), proceso, 0, ahora, null, null, null, null, 0L);
    }

    /** Para el adaptador de persistencia. */
    public static OrdenRoot rehidratar(Proceso<?> proceso, int intentos, Instant proximoReintentoEn,
            UUID tokenTrabajador, Instant tokenExpiraEn, Instant ticketAbiertoEn,
            ResultadoOrden resultado, long version) {
        return new OrdenRoot(proceso.id(), proceso, intentos, proximoReintentoEn, tokenTrabajador,
                tokenExpiraEn, ticketAbiertoEn, resultado, version);
    }

    public void asignarToken(UUID token, Duration lease, Instant ahora) {
        this.tokenTrabajador = token;
        this.tokenExpiraEn = ahora.plus(lease);
    }

    /** En la tx de cada paso completado: evita que el lease venza a media orden larga. */
    public void renovarLease(Duration lease, Instant ahora) {
        this.tokenExpiraEn = ahora.plus(lease);
    }

    public boolean tieneTokenVigente(Instant ahora) {
        return tokenTrabajador != null && tokenExpiraEn != null && tokenExpiraEn.isAfter(ahora);
    }

    public void liberarToken() {
        this.tokenTrabajador = null;
        this.tokenExpiraEn = null;
    }

    /** Paso OK: la orden vuelve a estar "sana". Un atasco posterior abrirá un ticket NUEVO. */
    public void resetearIntentos() {
        this.intentos = 0;
        this.ticketAbiertoEn = null;
    }

    public void marcarTicketAbierto(Instant ahora) {
        this.ticketAbiertoEn = ahora;
    }

    /** Eventos externos o intervención de soporte: la orden es candidata inmediata. */
    public void despertar(Instant ahora) {
        this.proximoReintentoEn = ahora;
        liberarToken();
    }

    /** Secundaria2 esperando el evento Kafka de respuesta. */
    public void aparcar(Duration ventana, Instant ahora) {
        this.proximoReintentoEn = ahora.plus(ventana);
        liberarToken();
    }

    public void programarReintento(PoliticaReintentos politica, Instant ahora) {
        this.intentos++;
        this.proximoReintentoEn = ahora.plus(politica.esperaTras(intentos));
        liberarToken();
    }

    public void finalizar(ResultadoOrden resultado) {
        this.resultado = resultado;
        liberarToken();
    }

    public boolean estaViva() {
        return resultado == null;
    }

    public Proceso<?> proceso() { return proceso; }
    public TipoOrden tipo() { return proceso.tipo(); }

    public OrdenId id() { return id; }
    public int intentos() { return intentos; }
    public Instant proximoReintentoEn() { return proximoReintentoEn; }
    public UUID tokenTrabajador() { return tokenTrabajador; }
    public Instant tokenExpiraEn() { return tokenExpiraEn; }
    public Instant ticketAbiertoEn() { return ticketAbiertoEn; }
    public ResultadoOrden resultado() { return resultado; }
    public long version() { return version; }
}
