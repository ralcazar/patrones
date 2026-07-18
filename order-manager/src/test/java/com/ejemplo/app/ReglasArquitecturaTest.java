package com.ejemplo.app;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
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

    /**
     * La frontera transaccional es cosa de la aplicación; el dominio no conoce
     * jakarta.transaction, ni el de ordermanager ni el de sagas.
     */
    @ArchTest
    static final ArchRule soloAplicacionUsaTransactional = noClasses()
            .that().resideInAnyPackage("..business..dominio..")
            .should().dependOnClassesThat().resideInAPackage("jakarta.transaction..")
            .because("jakarta.transaction.Transactional marca la frontera transaccional de los servicios"
                    + " de aplicación; el dominio es Java puro + jMolecules sin excepciones");

    @ArchTest
    static final ArchRule businessNoDependeDeInfraestructura = noClasses()
            .that().resideInAPackage("com.ejemplo.app.business..")
            .should().dependOnClassesThat().resideInAPackage("com.ejemplo.app.infraestructure..")
            .because("la dependencia va siempre de infraestructura hacia business, nunca al revés");

    /**
     * OrdenRoot (el único {@code @AggregateRoot}) y Proceso (la entidad interna
     * que contiene) son el modelo de dominio del motor, nunca infraestructura ni JPA.
     */
    @ArchTest
    static final ArchRule ordenRootYProcesoVivenEnElDominio = classes()
            .that().haveSimpleName("OrdenRoot").or().haveSimpleName("Proceso")
            .should().resideInAPackage("com.ejemplo.app.business.ordermanager.dominio")
            .because("son el agregado y su entidad interna, y viven en el dominio, no en infraestructura");

    /** JPA (@Entity de jakarta.persistence) solo vive en infraestructure, nunca en business. */
    @ArchTest
    static final ArchRule entidadesJpaVivenEnInfraestructura = classes()
            .that().areAnnotatedWith(jakarta.persistence.Entity.class)
            .should().resideInAPackage("com.ejemplo.app.infraestructure..")
            .because("las entidades JPA son infraestructura; el dominio (OrdenRoot/Proceso) es Java puro + jMolecules");

    /**
     * El motor de órdenes (ordermanager) es genérico en el tipo de orden: no
     * conoce las sagas concretas. La dirección legal es sagas -> ordermanager,
     * nunca al revés. Se comprueba sobre producción Y tests: los fixtures de
     * ordermanager usan dobles genéricos (ProcesadorOrdenFalso, ProcesoFalso)
     * en vez de las sagas concretas; el único test que necesitaba una saga
     * concreta con contexto Spring real (FronteraTransaccionalIntegrationTest)
     * vive en infraestructure.sagas.
     */
    @ArchTest
    static final ArchRule ordermanagerNoDependeDeSagas = noClasses()
            .that().resideInAnyPackage("..business.ordermanager..", "..infraestructure.ordermanager..")
            .should().dependOnClassesThat().resideInAnyPackage("..business.sagas..", "..infraestructure.sagas..")
            .because("el motor (ordermanager) debe poder reutilizarse con otros tipos de orden sin conocer las sagas");

    /** Vocabulario neutro: el motor no puede nombrar el concepto "saga" en sus propias clases. */
    @ArchTest
    static final ArchRule ordermanagerSinVocabularioDeSagas = noClasses()
            .that().resideInAnyPackage("..ordermanager..")
            .should().haveNameMatching(".*Saga.*")
            .because("ordermanager es el motor genérico: \"saga\" es vocabulario de business.sagas, no suyo");

    /**
     * Endurecimiento de la separación de source sets: src/test son los tests
     * UNITARIOS (Java puro + dobles en memoria, ver CLAUDE.md); si alguno
     * necesita Spring de verdad, va a src/integrationTest, no aquí. Se detecta
     * por la ubicación real del .class (compilado bajo .../classes/java/test/),
     * no por convención de nombre, para que no se pueda colar sin que salte.
     */
    private static final DescribedPredicate<JavaClass> RESIDE_EN_SRC_TEST =
            new DescribedPredicate<>("residen en el source set src/test") {
                @Override
                public boolean test(JavaClass javaClass) {
                    return javaClass.getSource()
                            .map(source -> source.getUri().toString().contains("/classes/java/test/"))
                            .orElse(false);
                }
            };

    @ArchTest
    static final ArchRule srcTestNoDependeDeSpring = noClasses()
            .that(RESIDE_EN_SRC_TEST)
            .should().dependOnClassesThat().resideInAPackage("org.springframework..")
            .because("src/test son los tests unitarios (sin Spring); los que necesiten Spring van a src/integrationTest");

    /**
     * El harness de pruebas de carga (source set {@code src/pruebaCarga},
     * paquete {@code com.ejemplo.app.carga}) es un consumidor de producción,
     * nunca al revés: ni PROD ni los tests dependen de él. El classpath ya
     * lo impone (pruebaCarga no está en el classpath de main/test/
     * integrationTest, ver build.gradle), pero esta regla documenta la
     * frontera explícitamente y actúa como red de seguridad si eso cambiara.
     */
    @ArchTest
    static final ArchRule nadaFueraDeCargaDependeDeCarga = noClasses()
            .that().resideOutsideOfPackage("..carga..")
            .should().dependOnClassesThat().resideInAPackage("com.ejemplo.app.carga..")
            .because("carga (src/pruebaCarga) es un harness que consume producción, nunca al revés");
}
