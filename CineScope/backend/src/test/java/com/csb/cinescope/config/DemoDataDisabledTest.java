package com.csb.cinescope.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.csb.cinescope.AbstractApiTest;

/** Demo data is off unless asked for: with the normal settings no seeder exists and no demo account is created. */
class DemoDataDisabledTest extends AbstractApiTest {

    @Test
    void withoutTheSwitchThereIsNoSeederAndNoDemoAccount() {
        assertEquals(0, context.getBeanNamesForType(DemoDataInitializer.class).length);
        for (DemoDataInitializer.DemoUser demo : DemoDataInitializer.USERS) {
            assertTrue(userRepository.findByUsername(demo.username()).isEmpty(), demo.username());
        }
    }

    @Test
    void withoutTheInitialAdminSettingThereIsNoBootstrapAndNobodyIsPromoted() {
        assertEquals(0, context.getBeanNamesForType(AdminBootstrap.class).length);
        assertTrue(userRepository.findAll().stream().noneMatch(u -> u.getRole() == com.csb.cinescope.entity.Role.ADMIN));
    }
}
