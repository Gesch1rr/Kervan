package com.eyup.kervan;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

// Checks module boundaries from the compiled classes (ArchUnit), without starting Spring:
// no cycles, no access to another module's internal packages, only the declared allowedDependencies.
class ModularityTest {

    private final ApplicationModules modules = ApplicationModules.of(KervanApplication.class);

    @Test
    void modulesRespectTheirBoundaries() {
        modules.verify();
    }

    // Writes PlantUML diagrams and module docs to target/spring-modulith-docs.
    @Test
    void writeModuleDocumentation() {
        new Documenter(modules).writeDocumentation();
    }
}
