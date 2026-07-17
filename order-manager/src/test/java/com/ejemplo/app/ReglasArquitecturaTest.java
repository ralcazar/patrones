package com.ejemplo.app;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Hace ejecutable la norma del CLAUDE.md: las capas business (dominio +
 * aplicación) solo usan Java puro y jMolecules; los frameworks viven
 * exclusivamente en infraestructure.
 */
@AnalyzeClasses(packages = "com.ejemplo.app")
class ReglasArquitecturaTest {

    /**
     * {@code jakarta.transaction..} NO está en esta lista a propósito: es la
     * única excepción de framework permitida en business (ver CLAUDE.md),
     * para marcar la frontera transaccional con {@code @Transactional} en los
     * servicios de aplicación. Sigue prohibido en el dominio (ver
     * {@link #soloAplicacionUsaTransactional}).
     */
    @ArchTest
    static final ArchRule businessSinFrameworks = noClasses()
            .that().resideInAPackage("com.ejemplo.app.business..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "com.fasterxml..",
                    "org.apache.kafka..")
            .because("business (dominio + aplicación) se escribe solo con Java puro y jMolecules"
                    + " (única excepción: jakarta.transaction.Transactional en aplicacion/servicio)");

    /** La frontera transaccional es cosa de la aplicación; el dominio no conoce jakarta.transaction. */
    @ArchTest
    static final ArchRule soloAplicacionUsaTransactional = noClasses()
            .that().resideInAPackage("com.ejemplo.app.business.ordermanager.dominio..")
            .should().dependOnClassesThat().resideInAPackage("jakarta.transaction..")
            .because("jakarta.transaction.Transactional marca la frontera transaccional de los servicios"
                    + " de aplicación; el dominio es Java puro + jMolecules sin excepciones");

    @ArchTest
    static final ArchRule businessNoDependeDeInfraestructura = noClasses()
            .that().resideInAPackage("com.ejemplo.app.business..")
            .should().dependOnClassesThat().resideInAPackage("com.ejemplo.app.infraestructure..")
            .because("la dependencia va siempre de infraestructura hacia business, nunca al revés");

    /**
     * OrdenRoot (el único {@code @AggregateRoot}) y Saga (la entidad interna
     * que contiene) son el modelo de dominio, nunca infraestructura ni JPA.
     */
    @ArchTest
    static final ArchRule ordenRootYSagaVivenEnElDominio = classes()
            .that().haveSimpleName("OrdenRoot").or().haveSimpleName("Saga")
            .should().resideInAPackage("com.ejemplo.app.business.ordermanager.dominio.comun")
            .because("son el agregado y su entidad interna, y viven en el dominio, no en infraestructura");

    /** JPA (@Entity de jakarta.persistence) solo vive en infraestructure, nunca en business. */
    @ArchTest
    static final ArchRule entidadesJpaVivenEnInfraestructura = classes()
            .that().areAnnotatedWith(jakarta.persistence.Entity.class)
            .should().resideInAPackage("com.ejemplo.app.infraestructure..")
            .because("las entidades JPA son infraestructura; el dominio (OrdenRoot/Saga) es Java puro + jMolecules");

    /**
     * El motor de órdenes (ordermanager) es genérico en el tipo de orden: no
     * conoce las sagas concretas. La dirección legal es sagas -> ordermanager,
     * nunca al revés.
     *
     * Se comprueba solo sobre producción ({@code DO_NOT_INCLUDE_TESTS}): hoy
     * varios tests de ordermanager (p. ej. ServicioContinuarSagaTest,
     * OrdenRootTest, RepositorioOrdenEnMemoria, FronteraTransaccionalIntegrationTest)
     * todavía construyen sagas concretas para montar sus fixtures. Ese
     * desacoplo de tests (dobles genéricos, mover FronteraTransaccionalIntegrationTest
     * a infraestructure.sagas) es trabajo del Paso 5 del plan de separación,
     * no de este paso.
     */
    @Test
    void ordermanagerNoDependeDeSagasEnProduccion() {
        JavaClasses clasesDeProduccion = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.ejemplo.app");
        ArchRule regla = noClasses()
                .that().resideInAnyPackage("..business.ordermanager..", "..infraestructure.ordermanager..")
                .should().dependOnClassesThat().resideInAnyPackage("..business.sagas..", "..infraestructure.sagas..")
                .because("el motor (ordermanager) debe poder reutilizarse con otros tipos de orden sin conocer las sagas");
        regla.check(clasesDeProduccion);
    }
}
