package com.ejemplo.app;

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

    @ArchTest
    static final ArchRule businessSinFrameworks = noClasses()
            .that().resideInAPackage("com.ejemplo.app.business..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "com.fasterxml..",
                    "org.apache.kafka..")
            .because("business (dominio + aplicación) se escribe solo con Java puro y jMolecules");

    @ArchTest
    static final ArchRule businessNoDependeDeInfraestructura = noClasses()
            .that().resideInAPackage("com.ejemplo.app.business..")
            .should().dependOnClassesThat().resideInAPackage("com.ejemplo.app.infraestructure..")
            .because("la dependencia va siempre de infraestructura hacia business, nunca al revés");

    /**
     * El agregado único (Fase 4 del refactor SagaRoot/OrdenRoot): OrdenRoot y
     * SagaRoot son el modelo de dominio, nunca infraestructura ni JPA.
     */
    @ArchTest
    static final ArchRule ordenRootYSagaRootVivenEnElDominio = classes()
            .that().haveSimpleName("OrdenRoot").or().haveSimpleName("SagaRoot")
            .should().resideInAPackage("com.ejemplo.app.business.ordermanager.dominio.comun")
            .because("son el ÚNICO agregado por saga y viven en el dominio, no en infraestructura");

    /** JPA (@Entity de jakarta.persistence) solo vive en infraestructure, nunca en business. */
    @ArchTest
    static final ArchRule entidadesJpaVivenEnInfraestructura = classes()
            .that().areAnnotatedWith(jakarta.persistence.Entity.class)
            .should().resideInAPackage("com.ejemplo.app.infraestructure..")
            .because("las entidades JPA son infraestructura; el dominio (OrdenRoot/SagaRoot) es Java puro + jMolecules");
}
