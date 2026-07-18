package com.ejemplo.app.business.ordermanager.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Estado de EJECUCIÓN del agregado único (OrdenRoot): reintentos, lease del
 * token y marca de ticket. Reloj determinista: todos los `Instant` los aporta
 * el test, nunca `Instant.now()`.
 */
class OrdenRootTest {

    private static final Duration LEASE = Duration.ofMinutes(10);
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private static Proceso<?> procesoCualquiera() {
        return ProcesoFalso.crear(OrdenId.nuevo(), ExternalId.de(UUID.randomUUID().toString()));
    }

    @Test
    void nueva_arrancaSinTokenSinTicketSinResultadoYVersionCero() {
        var orden = OrdenRoot.nueva(procesoCualquiera(), T0);

        assertThat(orden.intentos()).isZero();
        assertThat(orden.proximoReintentoEn()).isEqualTo(T0);
        assertThat(orden.tokenTrabajador()).isNull();
        assertThat(orden.tokenExpiraEn()).isNull();
        assertThat(orden.ticketAbiertoEn()).isNull();
        assertThat(orden.resultado()).isNull();
        assertThat(orden.estaViva()).isTrue();
        assertThat(orden.version()).isZero();
    }

    @Test
    void asignarToken_fijaLeaseSegunAhoraMasDuracion() {
        var orden = OrdenRoot.nueva(procesoCualquiera(), T0);
        var token = UUID.randomUUID();

        orden.asignarToken(token, LEASE, T0);

        assertThat(orden.tokenTrabajador()).isEqualTo(token);
        assertThat(orden.tokenExpiraEn()).isEqualTo(T0.plus(LEASE));
    }

    @Test
    void tieneTokenVigente_esFalsoSiNuncaSeAsigno() {
        var orden = OrdenRoot.nueva(procesoCualquiera(), T0);

        assertThat(orden.tieneTokenVigente(T0)).isFalse();
    }

    @Test
    void tieneTokenVigente_esVerdaderoAntesDeVencerYFalsoAlVencerOSuperar() {
        var orden = OrdenRoot.nueva(procesoCualquiera(), T0);
        orden.asignarToken(UUID.randomUUID(), LEASE, T0);
        var vencimiento = T0.plus(LEASE);

        assertThat(orden.tieneTokenVigente(vencimiento.minusSeconds(1))).isTrue();
        // "lease vencido": en el instante exacto de expiración ya no es vigente (reaparece como candidata).
        assertThat(orden.tieneTokenVigente(vencimiento)).isFalse();
        assertThat(orden.tieneTokenVigente(vencimiento.plusSeconds(1))).isFalse();
    }

    @Test
    void renovarLease_extiendeLaExpiracionDesdeElNuevoAhora() {
        var orden = OrdenRoot.nueva(procesoCualquiera(), T0);
        orden.asignarToken(UUID.randomUUID(), LEASE, T0);
        var masTarde = T0.plusSeconds(30);

        orden.renovarLease(LEASE, masTarde);

        assertThat(orden.tokenExpiraEn()).isEqualTo(masTarde.plus(LEASE));
    }

    @Test
    void liberarToken_dejaTokenYExpiracionNulos() {
        var orden = OrdenRoot.nueva(procesoCualquiera(), T0);
        orden.asignarToken(UUID.randomUUID(), LEASE, T0);

        orden.liberarToken();

        assertThat(orden.tokenTrabajador()).isNull();
        assertThat(orden.tokenExpiraEn()).isNull();
    }

    @Test
    void resetearIntentos_ponePasoOkYCierraElTicketAbierto() {
        var orden = OrdenRoot.nueva(procesoCualquiera(), T0);
        var politica = new PoliticaReintentos();
        for (int i = 0; i < 8; i++) {
            orden.programarReintento(politica, T0);
        }
        orden.marcarTicketAbierto(T0);
        assertThat(orden.intentos()).isEqualTo(8);
        assertThat(orden.ticketAbiertoEn()).isNotNull();

        orden.resetearIntentos();

        assertThat(orden.intentos()).isZero();
        assertThat(orden.ticketAbiertoEn()).isNull();
    }

    @Test
    void despertar_fijaProximoReintentoAhoraYLiberaToken() {
        var orden = OrdenRoot.nueva(procesoCualquiera(), T0);
        orden.asignarToken(UUID.randomUUID(), LEASE, T0);
        var ahora = T0.plusSeconds(5);

        orden.despertar(ahora);

        assertThat(orden.proximoReintentoEn()).isEqualTo(ahora);
        assertThat(orden.tokenTrabajador()).isNull();
    }

    @Test
    void aparcar_fijaProximoReintentoTrasLaVentanaYLiberaToken() {
        var orden = OrdenRoot.nueva(procesoCualquiera(), T0);
        orden.asignarToken(UUID.randomUUID(), LEASE, T0);
        var ventana = Duration.ofHours(3);

        orden.aparcar(ventana, T0);

        assertThat(orden.proximoReintentoEn()).isEqualTo(T0.plus(ventana));
        assertThat(orden.tokenTrabajador()).isNull();
    }

    @Test
    void programarReintento_incrementaIntentosSiguiendoLaEscaleraYLiberaToken() {
        var orden = OrdenRoot.nueva(procesoCualquiera(), T0);
        orden.asignarToken(UUID.randomUUID(), LEASE, T0);
        var politica = new PoliticaReintentos();

        orden.programarReintento(politica, T0);

        assertThat(orden.intentos()).isEqualTo(1);
        assertThat(orden.proximoReintentoEn()).isEqualTo(T0.plus(Duration.ofMinutes(1)));
        assertThat(orden.tokenTrabajador()).isNull();
    }

    @Test
    void programarReintento_repetidoSigueLaEscaleraCompletaYLuegoSeQuedaEn180() {
        var orden = OrdenRoot.nueva(procesoCualquiera(), T0);
        var politica = new PoliticaReintentos();
        var esperadas = List.of(1, 3, 5, 10, 20, 45, 90, 180, 180, 180);

        var ahora = T0;
        for (int minutosEsperados : esperadas) {
            orden.programarReintento(politica, ahora);
            assertThat(orden.proximoReintentoEn()).isEqualTo(ahora.plus(Duration.ofMinutes(minutosEsperados)));
            ahora = orden.proximoReintentoEn();
        }
        assertThat(orden.intentos()).isEqualTo(esperadas.size());
    }

    @Test
    void finalizar_fijaResultadoYLiberaTokenYDejaDeEstarViva() {
        var orden = OrdenRoot.nueva(procesoCualquiera(), T0);
        orden.asignarToken(UUID.randomUUID(), LEASE, T0);

        orden.finalizar(ResultadoOrden.FINALIZADA_OK);

        assertThat(orden.resultado()).isEqualTo(ResultadoOrden.FINALIZADA_OK);
        assertThat(orden.tokenTrabajador()).isNull();
        assertThat(orden.estaViva()).isFalse();
    }

    @Test
    void tieneTokenVigente_esFalsoSiHayTokenTrabajadorPeroSinFechaDeExpiracion() {
        // Combinación solo alcanzable vía rehidratar (asignarToken siempre fija ambos a la vez).
        var orden = OrdenRoot.rehidratar(procesoCualquiera(), 0, T0, UUID.randomUUID(), null, null, null, 0L);

        assertThat(orden.tieneTokenVigente(T0)).isFalse();
    }

    @Test
    void rehidratar_reconstruyeElAgregadoTalCualConSuVersion() {
        var proceso = procesoCualquiera();
        var token = UUID.randomUUID();
        var orden = OrdenRoot.rehidratar(proceso, 4, T0.plusSeconds(10), token, T0.plus(LEASE),
                T0.minusSeconds(1), null, 7L);

        assertThat(orden.intentos()).isEqualTo(4);
        assertThat(orden.tokenTrabajador()).isEqualTo(token);
        assertThat(orden.version()).isEqualTo(7L);
        assertThat(orden.tipo()).isEqualTo(ProcesoFalso.TIPO);
        assertThat(orden.proceso().estado()).isEqualTo(ProcesoFalso.Estado.INICIAL);
    }
}
