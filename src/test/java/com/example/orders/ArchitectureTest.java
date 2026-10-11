package com.example.orders;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces the layering described in CLAUDE.md. If a change breaks one of these rules,
 * fix the design rather than the rule.
 */
@AnalyzeClasses(packages = "com.example.orders", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule layers = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Api").definedBy("..api..")
            .layer("Service").definedBy("..service..")
            .layer("Persistence").definedBy("..persistence..")
            .layer("Messaging").definedBy("..messaging..")
            .layer("Config").definedBy("..config..")
            .whereLayer("Api").mayNotBeAccessedByAnyLayer()
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Api", "Messaging")
            .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Service", "Config")
            .whereLayer("Messaging").mayOnlyBeAccessedByLayers("Service");

    @ArchTest
    static final ArchRule controllersDoNotTouchRepositories = noClasses()
            .that().resideInAPackage("..api..")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository");

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "software.amazon..", "org.apache.kafka..", "com.fasterxml..");
}
