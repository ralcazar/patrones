package com.ejemplo.app.infraestructure.sagas.comun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

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

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoIncidencias;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenRoot;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.RepositorioDatosNegocio;
import com.ejemplo.app.business.sagas.aplicacion.servicio.comun.ServicioPurgarCompletadas;
import com.ejemplo.app.business.sagas.dominio.comun.ContextoArranque;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso1;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso5;
import com.ejemplo.app.business.sagas.dominio.comun.RefPaso7;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatoNegocio1;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatoNegocio2;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatoNegocio3;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatosNegocio;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatosNegocioId;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DocumentoNegocio;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.SagaPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.SagaSecundaria1;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.SagaSecundaria2;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.SagaSecundaria3;
import com.ejemplo.app.infraestructure.ordermanager.persistencia.AdaptadorRepositorioOrden;
import com.ejemplo.app.infraestructure.ordermanager.persistencia.OrdenEntity;
import com.ejemplo.app.infraestructure.ordermanager.persistencia.OrdenJpaRepository;
import com.ejemplo.app.infraestructure.sagas.datosnegocio.persistencia.AdaptadorDatosNegocio;
import com.ejemplo.app.infraestructure.sagas.datosnegocio.persistencia.DatosNegocioEntity;
import com.ejemplo.app.infraestructure.sagas.datosnegocio.persistencia.DatosNegocioJpaRepository;
import com.ejemplo.app.infraestructure.sagas.datosnegocio.persistencia.DocumentoNegocioJpaRepository;
import com.ejemplo.app.infraestructure.sagas.sagaprincipal.persistencia.ProcesoSagaPrincipalEntity;
import com.ejemplo.app.infraestructure.sagas.sagaprincipal.persistencia.ProcesoSagaPrincipalJpaRepository;
import com.ejemplo.app.infraestructure.sagas.sagaprincipal.persistencia.SoporteSagaPrincipal;
import com.ejemplo.app.infraestructure.sagas.sagasecundaria1.persistencia.ProcesoSagaSecundaria1Entity;
import com.ejemplo.app.infraestructure.sagas.sagasecundaria1.persistencia.ProcesoSagaSecundaria1JpaRepository;
import com.ejemplo.app.infraestructure.sagas.sagasecundaria1.persistencia.SoporteSagaSecundaria1;
import com.ejemplo.app.infraestructure.sagas.sagasecundaria2.persistencia.ProcesoSagaSecundaria2Entity;
import com.ejemplo.app.infraestructure.sagas.sagasecundaria2.persistencia.ProcesoSagaSecundaria2JpaRepository;
import com.ejemplo.app.infraestructure.sagas.sagasecundaria2.persistencia.SoporteSagaSecundaria2;
import com.ejemplo.app.infraestructure.sagas.sagasecundaria3.persistencia.ProcesoSagaSecundaria3Entity;
import com.ejemplo.app.infraestructure.sagas.sagasecundaria3.persistencia.ProcesoSagaSecundaria3JpaRepository;
import com.ejemplo.app.infraestructure.sagas.sagasecundaria3.persistencia.SoporteSagaSecundaria3;

/**
 * {@link ServicioPurgarCompletadas} de punta a punta sobre H2 real (modo
 * Oracle): demuestra que TODA la tramitación (4 órdenes + datos_negocio +
 * documentos) desaparece en una única transacción, y sobre todo que el orden
 * de borrado respeta la FK real que {@code proceso_saga_principal} (satélite
 * de la orden principal) tiene contra {@code datos_negocio} -- algo que
 * ningún test con dobles en memoria puede demostrar (el fake no modela FKs).
 */
@SpringBootTest(classes = ServicioPurgarCompletadasIntegrationTest.ContextoTest.class)
@ActiveProfiles("test")
class ServicioPurgarCompletadasIntegrationTest {

    private static final Instant AHORA = Instant.now();

    @Autowired
    private ServicioPurgarCompletadas servicio;

    @Autowired
    private AdaptadorRepositorioOrden repoOrdenes;

    @Autowired
    private AdaptadorDatosNegocio repoDatos;

    @Autowired
    private OrdenJpaRepository ordenJpaRepository;

    @Autowired
    private ProcesoSagaPrincipalJpaRepository procesoSagaPrincipalJpaRepository;

    @Autowired
    private DatosNegocioJpaRepository datosNegocioJpaRepository;

    @Autowired
    private DocumentoNegocioJpaRepository documentoNegocioJpaRepository;

    private static DatosNegocio nuevoDatosNegocio(DatosNegocioId id, ExternalId externalId) {
        return DatosNegocio.crear(id, externalId, new DatoNegocio1(1),
                new DatoNegocio2(LocalDate.of(2026, 1, 1)), new DatoNegocio3("dato"));
    }

    /** Crea la tramitación completa (datos_negocio + las 4 órdenes), todas terminadas hace 200 días. */
    private ExternalId crearTramitacionCompletadaHace200Dias() {
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var datosNegocioId = DatosNegocioId.nuevo();
        repoDatos.crear(nuevoDatosNegocio(datosNegocioId, externalId),
                List.of(new DocumentoNegocio("f.pdf", "application/pdf", new byte[] {1, 2, 3})));

        var completadaEn = AHORA.minusSeconds(200L * 24 * 3600);
        var principal = SagaPrincipal.crear(OrdenId.nuevo(), externalId, datosNegocioId);
        repoOrdenes.crear(OrdenRoot.rehidratar(principal, 0, AHORA, null, null, null, completadaEn, null, 0L));

        var ctx1 = new ContextoArranque.ArranqueSecundaria1(externalId, new RefPaso1("ref1"));
        repoOrdenes.crear(OrdenRoot.rehidratar(SagaSecundaria1.crear(OrdenId.nuevo(), ctx1), 0, AHORA,
                null, null, null, completadaEn, null, 0L));

        var ctx2 = new ContextoArranque.ArranqueSecundaria2(externalId, new RefPaso5("ref5"));
        repoOrdenes.crear(OrdenRoot.rehidratar(SagaSecundaria2.crear(OrdenId.nuevo(), ctx2), 0, AHORA,
                null, null, null, completadaEn, null, 0L));

        var ctx3 = new ContextoArranque.ArranqueSecundaria3(externalId, new RefPaso7("ref7"));
        repoOrdenes.crear(OrdenRoot.rehidratar(SagaSecundaria3.crear(OrdenId.nuevo(), ctx3), 0, AHORA,
                null, null, null, completadaEn, null, 0L));

        ordenJpaRepository.flush();
        return externalId;
    }

    @Test
    void ejecutar_borraDatosNegocioDocumentosYLas4OrdenesDeLaTramitacionEnUnaTransaccion() {
        var externalId = crearTramitacionCompletadaHace200Dias();
        var datosNegocioIdAntes = repoDatos.buscarPorExternalId(externalId).orElseThrow().id();
        assertThat(procesoSagaPrincipalJpaRepository.findAll().stream()
                .anyMatch(p -> p.getDatosnegocioId().equals(datosNegocioIdAntes.valor())))
                .as("la satélite con FK a datos_negocio existe antes de purgar").isTrue();

        servicio.ejecutar();

        assertThat(datosNegocioJpaRepository.findById(datosNegocioIdAntes.valor())).isEmpty();
        assertThat(documentoNegocioJpaRepository.findByDatosnegocioIdOrderBySecuenciaAsc(datosNegocioIdAntes.valor()))
                .isEmpty();
        assertThat(ordenJpaRepository.findAll().stream()
                .filter(o -> o.getExternalId().equals(externalId.valor().toString())))
                .isEmpty();
        assertThatThrownBy(() -> repoDatos.cargar(datosNegocioIdAntes)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ejecutar_conTramitacionViva_noBorraNada() {
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var datosNegocioId = DatosNegocioId.nuevo();
        repoDatos.crear(nuevoDatosNegocio(datosNegocioId, externalId), List.of());
        var principal = SagaPrincipal.crear(OrdenId.nuevo(), externalId, datosNegocioId);
        repoOrdenes.crear(OrdenRoot.nueva(principal, AHORA)); // viva: sin completadaEn
        ordenJpaRepository.flush();

        servicio.ejecutar();

        assertThat(repoDatos.cargar(datosNegocioId)).isNotNull();
    }

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {OrdenEntity.class, ProcesoSagaPrincipalEntity.class,
            ProcesoSagaSecundaria1Entity.class, ProcesoSagaSecundaria2Entity.class, ProcesoSagaSecundaria3Entity.class,
            DatosNegocioEntity.class})
    @EnableJpaRepositories(basePackageClasses = {OrdenEntity.class, ProcesoSagaPrincipalEntity.class,
            ProcesoSagaSecundaria1Entity.class, ProcesoSagaSecundaria2Entity.class, ProcesoSagaSecundaria3Entity.class,
            DatosNegocioEntity.class})
    static class ContextoTest {

        @Bean
        AdaptadorRepositorioOrden adaptadorRepositorioOrden(OrdenJpaRepository ordenes,
                ProcesoSagaPrincipalJpaRepository repoPrincipal, ProcesoSagaSecundaria1JpaRepository repoSecundaria1,
                ProcesoSagaSecundaria2JpaRepository repoSecundaria2, ProcesoSagaSecundaria3JpaRepository repoSecundaria3) {
            return new AdaptadorRepositorioOrden(ordenes, List.of(
                    new SoporteSagaPrincipal(repoPrincipal), new SoporteSagaSecundaria1(repoSecundaria1),
                    new SoporteSagaSecundaria2(repoSecundaria2), new SoporteSagaSecundaria3(repoSecundaria3)));
        }

        @Bean
        AdaptadorDatosNegocio adaptadorDatosNegocio(DatosNegocioJpaRepository datosNegocio,
                DocumentoNegocioJpaRepository documentos) {
            return new AdaptadorDatosNegocio(datosNegocio, documentos);
        }

        @Bean
        PuertoIncidencias puertoIncidencias() {
            return mock(PuertoIncidencias.class);
        }

        @Bean
        Clock clock() {
            return Clock.fixed(AHORA, ZoneOffset.UTC);
        }

        @Bean
        ServicioPurgarCompletadas servicioPurgarCompletadas(RepositorioOrden repo, RepositorioDatosNegocio repoDatos,
                PuertoIncidencias incidencias, Clock reloj, @Lazy ServicioPurgarCompletadas self) {
            var servicio = new ServicioPurgarCompletadas(repo, repoDatos, incidencias, reloj);
            servicio.establecerSelf(self);
            return servicio;
        }
    }
}
