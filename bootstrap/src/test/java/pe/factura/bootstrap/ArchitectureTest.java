package pe.factura.bootstrap;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "pe.factura", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule dominioNoDependeDeInfra = noClasses().that().resideInAPackage("pe.factura.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "pe.factura.application..", "pe.factura.adapters..", "pe.factura.bootstrap..",
                    "org.springframework..", "java.sql..", "javax.sql..", "freemarker..", "javax.xml.crypto..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule applicationNoDependeDeAdaptadores = noClasses().that().resideInAPackage("pe.factura.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "pe.factura.adapters..", "pe.factura.bootstrap..", "org.springframework..", "java.sql..", "freemarker..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule adaptadoresNoSeConocen = noClasses().that().resideInAPackage("pe.factura.adapters.(*)..")
            .should().dependOnClassesThat().resideInAPackage("pe.factura.adapters.(*)..")
            .allowEmptyShould(true);
}
