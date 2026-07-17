package com.ejemplo.app.infraestructure.ordermanager.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoSagaSecundaria3;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.sagas.aplicacion.servicio.sagasecundaria3.ServicioSagaSecundaria3;
import com.ejemplo.app.business.ordermanager.dominio.comun.ConcurrenciaOptimistaException;
import com.ejemplo.app.business.sagas.dominio.comun.ContextoArranque;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.comun.OrdenRoot;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso7;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.ComandoPasoSecundaria3;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.RefEjecucion;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.ResultadoPasoSecundaria3;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.SagaSecundaria3;

/**
 * Único test con contexto Spring real del proyecto: demuestra que la
 * frontera transaccional con {@code @Transactional} (Fase 3 del refactor,
 * Opción A: proxy auto-inyectado) se comporta como el diseño exige, algo que
 * ningún test unitario con dobles puede cubrir (el resto del proyecto usa
 * {@code RepositorioOrdenEnMemoria}, que no pasa por un proxy real).
 *
 * Usa {@link ServicioSagaSecundaria3} (el servicio de saga más simple: un
 * único REST y un único {@code aplicar}) sobre H2 en memoria con el esquema
 * real de Hibernate (ver {@code application-test.yml}), NO sobre el
 * {@code RepositorioOrden} en memoria de los tests unitarios.
 *
 * Verifica las tres propiedades que pide la Fase 3:
 * <ol>
 *   <li>el REST del paso ocurre FUERA de la transacción;</li>
 *   <li>un fallo dentro de {@code aplicar} hace rollback de todo lo escrito
 *       en esa transacción (incluido lo ya volcado a BD por el flush);</li>
 *   <li>el guardado respeta el optimistic lock real de JPA/Hibernate
 *       (no el simulado a mano del fake de los tests unitarios).</li>
 * </ol>
 */
@SpringBootTest(classes = FronteraTransaccionalIntegrationTest.ContextoTest.class)
@ActiveProfiles("test")
class FronteraTransaccionalIntegrationTest {

    @Autowired
    private RepositorioOrdenEspiaTx repo;

    @Autowired
    private ServicioSagaSecundaria3 servicioSaga;

    @Autowired
    private PuertoSagaSecundaria3Falso puerto;

    @BeforeEach
    void limpiarEspias() {
        repo.txActivaAlGuardar = null;
        repo.fallarDespuesDeGuardar = false;
        puerto.txActivaDuranteElRest = null;
    }

    private SagaId crearOrdenSecundaria3() {
        var id = SagaId.nuevo();
        var ctx = new ContextoArranque.ArranqueSecundaria3(
                ExternalId.de(UUID.randomUUID().toString()), new RefPaso7("ref7"));
        repo.crear(OrdenRoot.nueva(SagaSecundaria3.crear(id, ctx), Instant.now()));
        return id;
    }

    @Test
    void elRestDelPasoOcurreFueraDeLaTransaccionYAplicarDentro() {
        var id = crearOrdenSecundaria3();

        servicioSaga.ejecutarPaso(repo.cargar(id));

        assertThat(puerto.txActivaDuranteElRest).as("tx activa durante el REST del paso").isFalse();
        assertThat(repo.txActivaAlGuardar).as("tx activa al guardar dentro de aplicar()").isTrue();
    }

    @Test
    void unFalloDentroDeAplicarHaceRollbackDeLoYaEscritoEnEsaTransaccion() {
        var id = crearOrdenSecundaria3();
        var versionAntes = repo.cargar(id).version();
        var orden = repo.cargar(id); // una única carga por paso, antes del REST
        repo.fallarDespuesDeGuardar = true; // el guardado llega a la BD (flush) y LUEGO falla

        assertThatThrownBy(() -> servicioSaga.ejecutarPaso(orden))
                .isInstanceOf(FalloSimuladoException.class);

        var ordenTrasElFallo = repo.cargar(id);
        assertThat(ordenTrasElFallo.version()).as("version sin avanzar: el guardado hizo rollback")
                .isEqualTo(versionAntes);
        assertThat(ordenTrasElFallo.estaViva()).as("finalizar() nunca llegó a comprometerse").isTrue();
    }

    @Test
    void elGuardadoRespetaElOptimisticLockRealDeJpa() {
        var id = crearOrdenSecundaria3();
        var copiaA = repo.cargar(id); // dos instancias independientes de la MISMA fila,
        var copiaB = repo.cargar(id); // como dos pods que cargaron antes de que uno gane

        copiaA.resetearIntentos();
        repo.guardar(copiaA); // version N -> N+1: primer escritor gana

        copiaB.resetearIntentos();
        assertThatThrownBy(() -> repo.guardar(copiaB))
                .isInstanceOf(ConcurrenciaOptimistaException.class);
    }

    /** Envuelve el adaptador real y observa, sin tocarlo, el estado transaccional en cada guardado. */
    static final class RepositorioOrdenEspiaTx implements RepositorioOrden {

        private final RepositorioOrden delegado;
        volatile Boolean txActivaAlGuardar;
        volatile boolean fallarDespuesDeGuardar;

        RepositorioOrdenEspiaTx(RepositorioOrden delegado) {
            this.delegado = delegado;
        }

        @Override public void crear(OrdenRoot orden) { delegado.crear(orden); }

        @Override public OrdenRoot cargar(SagaId id) { return delegado.cargar(id); }

        @Override
        public void guardar(OrdenRoot orden) {
            txActivaAlGuardar = TransactionSynchronizationManager.isActualTransactionActive();
            delegado.guardar(orden); // el flush del adaptador real llega a la BD dentro de esta tx
            if (fallarDespuesDeGuardar) {
                throw new FalloSimuladoException();
            }
        }

        @Override
        public List<CandidataOrden> buscarEjecutables(Instant ahora, int limite) {
            return delegado.buscarEjecutables(ahora, limite);
        }

        @Override public boolean hayEjecutables(Instant ahora) { return delegado.hayEjecutables(ahora); }

        @Override public long purgarFinalizadasAntesDe(Instant corte) { return delegado.purgarFinalizadasAntesDe(corte); }
    }

    /** Test double de PuertoSagaSecundaria3: no hace I/O real, solo observa si hay tx activa. */
    static final class PuertoSagaSecundaria3Falso implements PuertoSagaSecundaria3 {

        volatile Boolean txActivaDuranteElRest;

        @Override
        public ResultadoPasoSecundaria3.Ejecutada ejecutar(ComandoPasoSecundaria3.Ejecutar cmd) {
            txActivaDuranteElRest = TransactionSynchronizationManager.isActualTransactionActive();
            return new ResultadoPasoSecundaria3.Ejecutada(new RefEjecucion("ejecucion-test"));
        }
    }

    static final class FalloSimuladoException extends RuntimeException {}

    /**
     * Contexto Spring mínimo: solo persistencia real (JPA sobre H2) + UN
     * ServicioSaga (el más simple) con su self-inyección vía {@code @Lazy},
     * igual que en ConfiguracionAplicacion. Deliberadamente NO reutiliza
     * ConfiguracionAplicacion completa: los demás servicios necesitan
     * adaptadores (Kafka, más puertos REST) que este esqueleto aún no
     * implementa.
     */
    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = OrdenEntity.class)
    @EnableJpaRepositories(basePackageClasses = OrdenEntity.class)
    static class ContextoTest {

        @Bean
        RepositorioOrdenEspiaTx repositorioOrden(OrdenJpaRepository ordenes, SagaJpaRepository sagas) {
            return new RepositorioOrdenEspiaTx(new AdaptadorRepositorioOrden(ordenes, sagas));
        }

        @Bean
        PuertoSagaSecundaria3Falso puertoSagaSecundaria3() {
            return new PuertoSagaSecundaria3Falso();
        }

        @Bean
        ServicioSagaSecundaria3 servicioSagaSecundaria3(RepositorioOrdenEspiaTx repo,
                PuertoSagaSecundaria3Falso puerto, @Lazy ServicioSagaSecundaria3 self) {
            var servicio = new ServicioSagaSecundaria3(repo, puerto);
            servicio.establecerSelf(self);
            return servicio;
        }
    }
}
