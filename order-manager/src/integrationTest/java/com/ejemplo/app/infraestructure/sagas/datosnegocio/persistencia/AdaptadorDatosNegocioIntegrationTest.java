package com.ejemplo.app.infraestructure.sagas.datosnegocio.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatoNegocio1;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatoNegocio2;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatoNegocio3;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatosNegocio;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatosNegocioId;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DocumentoNegocio;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.ExternalIdDuplicadoException;
import com.ejemplo.app.infraestructure.ordermanager.persistencia.OrdenEntity;
import com.ejemplo.app.infraestructure.ordermanager.persistencia.OrdenJpaRepository;

/**
 * Adaptador JPA real sobre H2 en memoria (modo Oracle, ver
 * application-test.yml): ejercita {@link AdaptadorDatosNegocio}, el agregado
 * de datos de negocio (Fase 1), incluida la purga de huérfanos (Fase 4) que
 * necesita también el esquema de {@code proceso} (motor de órdenes) para el
 * anti-join, así que el contexto de test escanea ambos paquetes de entidades.
 */
@SpringBootTest(classes = AdaptadorDatosNegocioIntegrationTest.ContextoTest.class)
@ActiveProfiles("test")
class AdaptadorDatosNegocioIntegrationTest {

    @Autowired
    private AdaptadorDatosNegocio repo;

    @Autowired
    private DatosNegocioJpaRepository datosNegocioJpaRepository;

    @Autowired
    private DocumentoNegocioJpaRepository documentoNegocioJpaRepository;

    @Autowired
    private OrdenJpaRepository ordenJpaRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private static DatosNegocio nuevoDatosNegocio(DatosNegocioId id, ExternalId externalId) {
        return DatosNegocio.crear(id, externalId, new DatoNegocio1(10),
                new DatoNegocio2(LocalDate.of(2026, 7, 18)), new DatoNegocio3("dato de negocio"));
    }

    @Test
    void crearYDocumentosDe_recuperaLosBlobsIdenticosByteAByte() {
        var id = DatosNegocioId.nuevo();
        var datosNegocio = nuevoDatosNegocio(id, ExternalId.de(UUID.randomUUID().toString()));
        var doc1 = new DocumentoNegocio("factura.pdf", "application/pdf", new byte[] {1, 2, 3, 4, 5});
        var doc2 = new DocumentoNegocio("anexo.png", "image/png", new byte[] {9, 8, 7});

        repo.crear(datosNegocio, List.of(doc1, doc2));

        var documentos = repo.documentosDe(id);

        assertThat(documentos).hasSize(2);
        assertThat(documentos.get(0).nombre()).isEqualTo("factura.pdf");
        assertThat(documentos.get(0).mimeType()).isEqualTo("application/pdf");
        assertThat(documentos.get(0).contenido()).isEqualTo(new byte[] {1, 2, 3, 4, 5});
        assertThat(documentos.get(1).nombre()).isEqualTo("anexo.png");
        assertThat(documentos.get(1).contenido()).isEqualTo(new byte[] {9, 8, 7});
    }

    @Test
    void crear_sinDocumentos_noGuardaNingunDocumento() {
        var id = DatosNegocioId.nuevo();
        repo.crear(nuevoDatosNegocio(id, ExternalId.de(UUID.randomUUID().toString())), List.of());

        assertThat(repo.documentosDe(id)).isEmpty();
    }

    @Test
    void cargar_devuelveSoloLosEscalaresSinTocarLosDocumentos() {
        var id = DatosNegocioId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var datosNegocio = nuevoDatosNegocio(id, externalId);
        repo.crear(datosNegocio, List.of(new DocumentoNegocio("f.pdf", "application/pdf", new byte[] {1})));

        var recargado = repo.cargar(id);

        assertThat(recargado.id()).isEqualTo(id);
        assertThat(recargado.externalId()).isEqualTo(externalId);
        assertThat(recargado.datoNegocio1()).isEqualTo(datosNegocio.datoNegocio1());
        assertThat(recargado.datoNegocio2()).isEqualTo(datosNegocio.datoNegocio2());
        assertThat(recargado.datoNegocio3()).isEqualTo(datosNegocio.datoNegocio3());
    }

    @Test
    void crear_dejaPurgadoEnANullHastaQueSePurgueLaTramitacion() {
        var id = DatosNegocioId.nuevo();
        repo.crear(nuevoDatosNegocio(id, ExternalId.de(UUID.randomUUID().toString())), List.of());

        var entity = datosNegocioJpaRepository.findById(id.valor()).orElseThrow();

        assertThat(entity.getPurgadoEn()).isNull();
    }

    @Test
    void cargar_inexistente_lanzaIllegalArgumentException() {
        assertThatThrownBy(() -> repo.cargar(DatosNegocioId.nuevo()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void crear_conExternalIdDuplicado_traduceLaViolacionDelIndiceUnicoAExternalIdDuplicadoException() {
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        repo.crear(nuevoDatosNegocio(DatosNegocioId.nuevo(), externalId), List.of());

        assertThatThrownBy(() -> repo.crear(nuevoDatosNegocio(DatosNegocioId.nuevo(), externalId), List.of()))
                .isInstanceOf(ExternalIdDuplicadoException.class);
    }

    /**
     * Reproduce el caso real de producción: {@code crear} se invoca desde
     * DENTRO de la transacción de {@code ServicioIniciarTramitacion.crearAgregados}
     * (no como llamada suelta, que es lo que ejercitan los demás tests de esta
     * clase). Sin el {@code flush()} explícito en {@link AdaptadorDatosNegocio#crear},
     * la violación del índice único se diferiría hasta el commit de ESA
     * transacción externa y llegaría sin traducir (DataIntegrityViolationException
     * cruda), rompiendo la idempotencia de ServicioIniciarTramitacion.
     */
    @Test
    void crear_conExternalIdDuplicado_dentroDeUnaTransaccionYaActiva_siguetraduciendoAExternalIdDuplicadoException() {
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var tt = new TransactionTemplate(transactionManager);

        tt.executeWithoutResult(status -> repo.crear(nuevoDatosNegocio(DatosNegocioId.nuevo(), externalId), List.of()));

        assertThatThrownBy(() -> tt.executeWithoutResult(status ->
                repo.crear(nuevoDatosNegocio(DatosNegocioId.nuevo(), externalId), List.of())))
                .isInstanceOf(ExternalIdDuplicadoException.class);
    }

    @Test
    void buscarPorExternalId_existente_loEncuentra() {
        var id = DatosNegocioId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        repo.crear(nuevoDatosNegocio(id, externalId), List.of());

        var encontrado = repo.buscarPorExternalId(externalId);

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().id()).isEqualTo(id);
    }

    @Test
    void buscarPorExternalId_inexistente_devuelveVacio() {
        var encontrado = repo.buscarPorExternalId(ExternalId.de(UUID.randomUUID().toString()));

        assertThat(encontrado).isEmpty();
    }

    /** Inserta una fila mínima en orden (sin pasar por el agregado OrdenRoot completo, ver CLAUDE.md tarea). */
    private void insertarProcesoCon(ExternalId externalId) {
        var ahora = Instant.now();
        ordenJpaRepository.save(new OrdenEntity(UUID.randomUUID(), "PRINCIPAL",
                externalId.valor().toString(), "INICIAL", List.of(),
                0, ahora, null, null, null, null, null, null, 0L));
    }

    @Test
    void idsHuerfanos_soloDevuelveLosDatosNegocioSinNingunProcesoConSuExternalId() {
        // El contexto Spring (y la BD H2) se comparte entre los tests de esta clase (sin
        // @Transactional/rollback por test), así que la aserción no puede ser un containsExactly
        // sobre el resultado completo: otros tests ya dejan huérfanos propios en la tabla.
        var externalIdHuerfano = ExternalId.de(UUID.randomUUID().toString());
        var idHuerfano = DatosNegocioId.nuevo();
        repo.crear(nuevoDatosNegocio(idHuerfano, externalIdHuerfano), List.of());

        var externalIdVivo = ExternalId.de(UUID.randomUUID().toString());
        var idVivo = DatosNegocioId.nuevo();
        repo.crear(nuevoDatosNegocio(idVivo, externalIdVivo), List.of());
        insertarProcesoCon(externalIdVivo); // el proceso comparte el externalId: NO es huérfano

        var huerfanos = repo.idsHuerfanos();

        assertThat(huerfanos).contains(idHuerfano).doesNotContain(idVivo);
    }

    @Test
    void borrar_borraLosDocumentosYElPropioRegistroDatosNegocio() {
        var id = DatosNegocioId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        repo.crear(nuevoDatosNegocio(id, externalId),
                List.of(new DocumentoNegocio("f.pdf", "application/pdf", new byte[] {1})));

        repo.borrar(id);

        assertThat(documentoNegocioJpaRepository.findByDatosnegocioIdOrderBySecuenciaAsc(id.valor())).isEmpty();
        assertThat(datosNegocioJpaRepository.findById(id.valor())).isEmpty();
    }

    /**
     * Contexto Spring de test: escanea DatosNegocioEntity (este agregado) Y
     * OrdenEntity (motor de órdenes), porque idsHuerfanos() hace un
     * anti-join nativo contra la tabla orden (ver DatosNegocioJpaRepository;
     * external_id vivía en proceso, fusionada en orden desde la fase 2).
     */
    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {DatosNegocioEntity.class, OrdenEntity.class})
    @EnableJpaRepositories(basePackageClasses = {DatosNegocioEntity.class, OrdenEntity.class})
    static class ContextoTest {

        @Bean
        AdaptadorDatosNegocio adaptadorDatosNegocio(DatosNegocioJpaRepository datosNegocio,
                DocumentoNegocioJpaRepository documentos) {
            return new AdaptadorDatosNegocio(datosNegocio, documentos);
        }
    }
}
