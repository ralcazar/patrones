package com.ejemplo.app;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

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
}
