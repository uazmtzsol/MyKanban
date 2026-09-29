package com.personalkanban.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Executable architecture: the layering rules from the design, enforced on
 * every build. Fails if the domain ever imports JavaFX or JDBC, or if layers
 * start depending in the wrong direction.
 */
class ArchitectureTest {

    private final com.tngtech.archunit.core.domain.JavaClasses classes =
            new ClassFileImporter(java.util.List.of(new ImportOption.DoNotIncludeTests()))
                    .importPackages("com.personalkanban");

    @Test
    void domainStaysPure() {
        ArchRule rule = noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "javafx..", "java.sql..", "org.xerial..",
                        "com.personalkanban.application..", "com.personalkanban.infrastructure..",
                        "com.personalkanban.ui..");
        rule.check(classes);
    }

    @Test
    void applicationDoesNotTouchUiOrJdbc() {
        ArchRule rule = noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "javafx..", "java.sql..", "org.xerial..", "com.personalkanban.ui..");
        rule.check(classes);
    }

    @Test
    void uiDoesNotTouchJdbcOrSqlite() {
        ArchRule rule = noClasses().that().resideInAPackage("..ui..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "java.sql..", "org.xerial..", "com.personalkanban.infrastructure..");
        rule.check(classes);
    }

    @Test
    void infrastructureIsOnlyReferencedByCompositionRoot() {
        ArchRule rule = noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..");
        rule.check(classes);
    }
}
