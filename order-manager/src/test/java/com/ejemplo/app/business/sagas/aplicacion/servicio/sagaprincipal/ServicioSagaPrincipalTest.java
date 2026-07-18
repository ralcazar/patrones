package com.ejemplo.app.business.sagas.aplicacion.servicio.sagaprincipal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoPaso1;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoPaso2;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoPaso3;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoPaso4;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoPaso5;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoPaso6;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoPaso7;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoPaso8;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ServicioContinuarOrden;
import com.ejemplo.app.testsoporte.RepositorioOrdenEnMemoria;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenRoot;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso1;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso5;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso7;
import com.ejemplo.app.business.ordermanager.dominio.ResultadoOrden;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.UsuarioSoporte;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.DatoNegocio2;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.DatoNegocio3;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.EstadoSagaPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.RefPaso2;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.RefPaso3;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.RefPaso4;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.RefPaso6;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.RefPaso8;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ResultadoPasoPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.SagaPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.SagaSecundaria1;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.SagaSecundaria2;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.SagaSecundaria3;

/**
 * Orquestación real de la saga principal a través de ServicioContinuarOrden:
 * flujo feliz de los 8 pasos con arranque de las 3 secundarias, y
 * cancelación -&gt; compensación -&gt; FINALIZADA_COMPENSADA (Fase 4 del refactor).
 */
class ServicioSagaPrincipalTest {

    private static final Duration LEASE = Duration.ofMinutes(10);

    private RepositorioOrdenEnMemoria repo;
    private PuertoPaso1 puertoPaso1;
    private PuertoPaso2 puertoPaso2;
    private PuertoPaso3 puertoPaso3;
    private PuertoPaso4 puertoPaso4;
    private PuertoPaso5 puertoPaso5;
    private PuertoPaso6 puertoPaso6;
    private PuertoPaso7 puertoPaso7;
    private PuertoPaso8 puertoPaso8;
    private ServicioSagaPrincipal servicioSaga;
    private ServicioContinuarOrden servicioContinuar;

    @BeforeEach
    void init() {
        repo = new RepositorioOrdenEnMemoria();
        puertoPaso1 = mock(PuertoPaso1.class);
        puertoPaso2 = mock(PuertoPaso2.class);
        puertoPaso3 = mock(PuertoPaso3.class);
        puertoPaso4 = mock(PuertoPaso4.class);
        puertoPaso5 = mock(PuertoPaso5.class);
        puertoPaso6 = mock(PuertoPaso6.class);
        puertoPaso7 = mock(PuertoPaso7.class);
        puertoPaso8 = mock(PuertoPaso8.class);
        servicioSaga = new ServicioSagaPrincipal(repo, LEASE, puertoPaso1, puertoPaso2, puertoPaso3,
                puertoPaso4, puertoPaso5, puertoPaso6, puertoPaso7, puertoPaso8);
        servicioContinuar = new ServicioContinuarOrden(Map.of(SagaPrincipal.TIPO, servicioSaga), repo,
                new com.ejemplo.app.business.ordermanager.dominio.PoliticaReintentos(), LEASE, 16);

        when(puertoPaso1.ejecutar(any())).thenReturn(new ResultadoPasoPrincipal.ResultadoPaso1(new RefPaso1("ref1")));
        when(puertoPaso2.ejecutar(any())).thenReturn(new ResultadoPasoPrincipal.ResultadoPaso2(new RefPaso2("ref2")));
        when(puertoPaso3.ejecutar(any())).thenReturn(new ResultadoPasoPrincipal.ResultadoPaso3(new RefPaso3("ref3")));
        when(puertoPaso4.ejecutar(any())).thenReturn(new ResultadoPasoPrincipal.ResultadoPaso4(new RefPaso4("ref4")));
        when(puertoPaso5.ejecutar(any())).thenReturn(new ResultadoPasoPrincipal.ResultadoPaso5(new RefPaso5("ref5")));
        when(puertoPaso6.ejecutar(any())).thenReturn(new ResultadoPasoPrincipal.ResultadoPaso6(new RefPaso6("ref6")));
        when(puertoPaso7.ejecutar(any())).thenReturn(new ResultadoPasoPrincipal.ResultadoPaso7(new RefPaso7("ref7")));
        when(puertoPaso8.ejecutar(any())).thenReturn(new ResultadoPasoPrincipal.ResultadoPaso8(new RefPaso8("ref8")));
    }

    private OrdenId crearOrdenPrincipal() {
        var id = OrdenId.nuevo();
        var saga = SagaPrincipal.crear(id, ExternalId.de(UUID.randomUUID().toString()),
                new DatoNegocio3("v1", "v2"), new DatoNegocio2("v1", "v2"));
        repo.crear(OrdenRoot.nueva(saga, Instant.now()));
        return id;
    }

    @Test
    void flujoFeliz_recorreLosOchoPasosEnUnaSolaLlamadaYArrancaLasTresSecundarias() {
        var id = crearOrdenPrincipal();

        servicioContinuar.continuarSiguiente();

        var ordenFinal = repo.estadoActual(id);
        assertThat(ordenFinal.resultado()).isEqualTo(ResultadoOrden.FINALIZADA_OK);
        assertThat(ordenFinal.estaViva()).isFalse();
        assertThat(((SagaPrincipal) ordenFinal.proceso()).estado()).isEqualTo(EstadoSagaPrincipal.TERMINADA);

        var hijas = repo.todas().stream().filter(o -> !SagaPrincipal.TIPO.equals(o.tipo())).toList();
        assertThat(hijas).hasSize(3);
        assertThat(hijas).extracting(OrdenRoot::tipo)
                .containsExactlyInAnyOrder(SagaSecundaria1.TIPO, SagaSecundaria2.TIPO, SagaSecundaria3.TIPO);
        assertThat(hijas).allSatisfy(h -> assertThat(h.estaViva()).isTrue());
    }

    @Test
    void compensacion_alCancelarTrasPaso2_encadenaHastaFinalizadaCompensada() {
        var id = crearOrdenPrincipal();

        // Avanza solo PASO1 y PASO2 antes de cancelar (para forzar la compensación de ambos).
        avanzarUnPaso(id); // PASO1
        avanzarUnPaso(id); // PASO2

        var ordenTrasPaso2 = repo.estadoActual(id);
        assertThat(((SagaPrincipal) ordenTrasPaso2.proceso()).estado()).isEqualTo(EstadoSagaPrincipal.PASO2_HECHO);

        // Soporte cancela: dispara la compensación PASO2 -> PASO1 -> CANCELADA.
        var ordenACancelar = repo.cargar(id);
        var sagaACancelar = (SagaPrincipal) ordenACancelar.proceso();
        sagaACancelar.cancelar(new UsuarioSoporte("ana"), "motivo de negocio");
        ordenACancelar.despertar(Instant.now());
        repo.guardar(ordenACancelar);

        servicioContinuar.continuarSiguiente();

        var ordenFinal = repo.estadoActual(id);
        assertThat(((SagaPrincipal) ordenFinal.proceso()).estado()).isEqualTo(EstadoSagaPrincipal.CANCELADA);
        assertThat(ordenFinal.resultado()).isEqualTo(ResultadoOrden.FINALIZADA_COMPENSADA);
        assertThat(ordenFinal.estaViva()).isFalse();
        verify(puertoPaso2).compensar(any());
        verify(puertoPaso1).compensar(any());
        // La cancelación fue antes del punto de no retorno: nunca se llegó a ejecutar PASO3 ni siguientes.
        verify(puertoPaso3, org.mockito.Mockito.never()).ejecutar(any());
    }

    @Test
    void podZombi_conRestColgadoMasQueElLease_fallaPorVersionYNoPisaNiSaltaPasos() {
        var id = crearOrdenPrincipal();
        avanzarUnPaso(id); // PASO1
        avanzarUnPaso(id); // PASO2

        // El REST de PASO3 del pod zombi "se cuelga": mientras está en vuelo, otro
        // pod hace takeover y ejecuta PASO3 de verdad (escribe y sube la version).
        when(puertoPaso3.ejecutar(any())).thenAnswer(invocacion -> {
            var ordenOtroPod = repo.cargar(id);
            ((SagaPrincipal) ordenOtroPod.proceso())
                    .aplicarYAvanzar(new ResultadoPasoPrincipal.ResultadoPaso3(new RefPaso3("ref3-otro-pod")));
            repo.guardar(ordenOtroPod);
            return new ResultadoPasoPrincipal.ResultadoPaso3(new RefPaso3("ref3-zombi"));
        });

        // El zombi despierta con su instantánea obsoleta: su guardar falla por version.
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> servicioSaga.ejecutarPaso(repo.cargar(id)))
                .isInstanceOf(com.ejemplo.app.business.ordermanager.dominio.ConcurrenciaOptimistaException.class);

        // Gana el otro pod: estado en PASO3_HECHO (sin saltar a PASO4_HECHO) y su ref intacta.
        var saga = (SagaPrincipal) repo.estadoActual(id).proceso();
        assertThat(saga.estado()).isEqualTo(EstadoSagaPrincipal.PASO3_HECHO);
        assertThat(saga.contexto().refPaso3()).isEqualTo(new RefPaso3("ref3-otro-pod"));
    }

    /** Ejecuta exactamente un paso invocando al servicioSaga directamente (no toca el token). */
    private void avanzarUnPaso(OrdenId id) {
        var antes = repo.estadoActual(id).version();
        servicioSaga.ejecutarPaso(repo.cargar(id));
        assertThat(repo.estadoActual(id).version()).isGreaterThan(antes);
    }

    @Test
    void tipo_devuelveElTipoDeLaSagaPrincipal() {
        assertThat(servicioSaga.tipo()).isEqualTo(SagaPrincipal.TIPO);
    }

    @Test
    void establecerSelf_sustituyeElProxyUsadoParaLaAutoInvocacionTransaccional() {
        var proxy = mock(ServicioSagaPrincipal.class);
        var id = crearOrdenPrincipal();
        when(proxy.aplicarPasoNormal(any(), any(), any())).thenReturn(new com.ejemplo.app.business.ordermanager.aplicacion.servicio.SenalPaso.HayMasTrabajo());

        servicioSaga.establecerSelf(proxy);
        var senal = servicioSaga.ejecutarPaso(repo.cargar(id));

        assertThat(senal).isInstanceOf(com.ejemplo.app.business.ordermanager.aplicacion.servicio.SenalPaso.HayMasTrabajo.class);
        verify(proxy).aplicarPasoNormal(any(), any(), any());
    }

    @Test
    void ejecutarComando_conCompensarPaso1_lanzaIllegalStateExceptionPorSerUnaViaNoSoportada() {
        var ctx = new RefPaso1("ref1");

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> servicioSaga.ejecutarComando(
                        new com.ejemplo.app.business.sagas.dominio.sagaprincipal.ComandoPasoPrincipal.CompensarPaso1(ctx)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ejecutarComando_conCompensarPaso2_lanzaIllegalStateExceptionPorSerUnaViaNoSoportada() {
        var ctx = new RefPaso2("ref2");

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> servicioSaga.ejecutarComando(
                        new com.ejemplo.app.business.sagas.dominio.sagaprincipal.ComandoPasoPrincipal.CompensarPaso2(ctx)))
                .isInstanceOf(IllegalStateException.class);
    }
}
