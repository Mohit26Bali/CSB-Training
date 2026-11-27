package com.icc.servicenow.mapping;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MappingTest {

    @Test
    void fourCategoriesKeepTheirWord_andTheOtherTwoAreTranslated() {
        assertEquals("hardware", CategoryMapping.category("Hardware"));
        assertEquals("software", CategoryMapping.category("Software"));
        assertEquals("network", CategoryMapping.category("Network"));
        assertEquals("database", CategoryMapping.category("Database"));
        assertEquals("password_reset", CategoryMapping.category("Access"));
        assertEquals("inquiry", CategoryMapping.category("Other"));
    }

    @Test
    void anUnknownCategory_isRefusedWithTheKnownOnes() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> CategoryMapping.category("Printing"));
        assertTrue(e.getMessage().contains("Known"));
    }

    @Test
    void subcategories_areLowerCase_exceptTwoSpecialOnes() {
        assertEquals("vpn", CategoryMapping.subcategory("VPN"));
        assertEquals("ip address", CategoryMapping.subcategory("IP Address"));
        assertEquals("email", CategoryMapping.subcategory("Email"));
        assertEquals("os", CategoryMapping.subcategory("Operating System"));
        assertEquals("sql server", CategoryMapping.subcategory("MS SQL Server"));
        assertNull(CategoryMapping.subcategory(null));
        assertNull(CategoryMapping.subcategory(" "));
    }

    @Test
    void incidentStates_matchTheInstanceNumbers() {
        assertEquals("1", StateMapping.incidentState("NEW"));
        assertEquals("2", StateMapping.incidentState("IN_PROGRESS"));
        assertEquals("3", StateMapping.incidentState("ON_HOLD"));
        assertEquals("6", StateMapping.incidentState("RESOLVED"));
        assertEquals("7", StateMapping.incidentState("CLOSED"));
        assertEquals("8", StateMapping.incidentState("CANCELLED"));
    }

    @Test
    void holdReasons_matchTheInstanceNumbers() {
        assertEquals("1", StateMapping.holdReason("AWAITING_CALLER"));
        assertEquals("3", StateMapping.holdReason("AWAITING_PROBLEM"));
        assertEquals("4", StateMapping.holdReason("AWAITING_VENDOR"));
        assertEquals("5", StateMapping.holdReason("AWAITING_CHANGE"));
    }

    @Test
    void problemStates_andResolutions_matchTheInstance() {
        assertEquals("101", StateMapping.problemState("NEW"));
        assertEquals("102", StateMapping.problemState("ASSESS"));
        assertEquals("103", StateMapping.problemState("ROOT_CAUSE_ANALYSIS"));
        assertEquals("104", StateMapping.problemState("FIX_IN_PROGRESS"));
        assertEquals("106", StateMapping.problemState("RESOLVED"));
        assertEquals("107", StateMapping.problemState("CLOSED"));
        assertEquals("fix_applied", StateMapping.problemResolution("FIX_APPLIED"));
        assertEquals("risk_accepted", StateMapping.problemResolution("RISK_ACCEPTED"));
        assertEquals("canceled", StateMapping.problemResolution("CANCELED"));
        assertEquals("duplicate", StateMapping.problemResolution("DUPLICATE"));
    }

    @Test
    void anUnknownState_isRefused() {
        assertThrows(IllegalArgumentException.class, () -> StateMapping.incidentState("DRAFT"));
        assertThrows(IllegalArgumentException.class, () -> StateMapping.problemState("SUBMITTED"));
    }

    @Test
    void theTenCloseCodes_pass_andAnythingElseIsRefusedBeforeTheCall() {
        List<String> ten = List.of("Duplicate", "Known error", "No resolution provided", "Resolved by caller", "Resolved by change",
                "Resolved by problem", "Resolved by request", "Solution provided", "Workaround provided", "User error");
        assertEquals(ten, ResolutionMapping.CLOSE_CODES);
        ten.forEach(code -> assertEquals(code, ResolutionMapping.closeCode(code)));
        assertThrows(IllegalArgumentException.class, () -> ResolutionMapping.closeCode("Solved (Permanently)"));
        assertThrows(IllegalArgumentException.class, () -> ResolutionMapping.closeCode(null));
    }
}
