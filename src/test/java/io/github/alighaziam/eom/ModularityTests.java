package io.github.alighaziam.eom;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    @Test
    void verifiesModuleBoundaries() {
        ApplicationModules.of(EnterpriseOrderManagementApplication.class).verify();
    }
}
