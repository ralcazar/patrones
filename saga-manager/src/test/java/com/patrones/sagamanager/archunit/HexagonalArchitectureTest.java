package com.patrones.sagamanager.archunit;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
		packages = "com.patrones.sagamanager",
		importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

	private static final String BASE = "com.patrones.sagamanager";

	@ArchTest
	static final ArchRule domain_must_not_depend_on_spring =
			noClasses().that().resideInAPackage(BASE + ".domain..")
					.should().dependOnClassesThat().resideInAnyPackage(
							"org.springframework..", "jakarta..", "javax.persistence..");

	@ArchTest
	static final ArchRule domain_must_not_depend_on_jpa_or_jakarta =
			noClasses().that().resideInAPackage(BASE + ".domain..")
					.should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "jakarta.validation..");

	@ArchTest
	static final ArchRule layered_dependencies_point_inwards = Architectures.layeredArchitecture()
			.consideringOnlyDependenciesInLayers()
			.layer("Domain").definedBy(BASE + ".domain..")
			.layer("Application").definedBy(BASE + ".application..")
			.layer("Infrastructure").definedBy(BASE + ".infrastructure..")
			.whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
			.whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
			.whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure");

	@ArchTest
	static final ArchRule adapters_live_in_infrastructure =
			noClasses().that().haveSimpleNameEndingWith("Client")
					.or().haveSimpleNameEndingWith("Listener")
					.or().haveSimpleNameEndingWith("Scheduler")
					.should().resideOutsideOfPackage(BASE + ".infrastructure..");
}
