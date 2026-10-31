package com.seniorinsight.identity;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.seniorinsight.identity", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaTest {

    private static final String DOMAIN = "com.seniorinsight.identity.domain..";
    private static final String APPLICATION = "com.seniorinsight.identity.application..";
    private static final String ADAPTERS = "com.seniorinsight.identity.adapters..";
    private static final String ADAPTERS_IN = "com.seniorinsight.identity.adapters.in..";
    private static final String ADAPTERS_OUT = "com.seniorinsight.identity.adapters.out..";

    @ArchTest
    static final ArchRule dominioNaoDependeDeSpringNemJpa = noClasses()
            .that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "org.hibernate..")
            .because("o domínio é Java puro");

    @ArchTest
    static final ArchRule dominioNaoDependeDeApplicationNemAdapters = noClasses()
            .that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat().resideInAnyPackage(APPLICATION, ADAPTERS);

    @ArchTest
    static final ArchRule applicationNaoDependeDeAdapters = noClasses()
            .that().resideInAPackage(APPLICATION)
            .should().dependOnClassesThat().resideInAPackage(ADAPTERS);

    @ArchTest
    static final ArchRule adaptersInNaoDependeDeAdaptersOut = noClasses()
            .that().resideInAPackage(ADAPTERS_IN)
            .should().dependOnClassesThat().resideInAPackage(ADAPTERS_OUT);

    @ArchTest
    static final ArchRule adaptersOutNaoDependeDeAdaptersIn = noClasses()
            .that().resideInAPackage(ADAPTERS_OUT)
            .should().dependOnClassesThat().resideInAPackage(ADAPTERS_IN);
}
