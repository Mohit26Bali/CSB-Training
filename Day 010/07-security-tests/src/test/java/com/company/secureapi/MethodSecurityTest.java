package com.company.secureapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;

import com.company.secureapi.security.AuthenticatedUser;
import com.company.secureapi.service.EmployeeService;

/**
 * The @PreAuthorize rules tested on the SERVICE directly: no HTTP, no URL rules.
 * Ownership rules need the employee id, so those tests put an AuthenticatedUser
 * (what the JWT filter creates) into the SecurityContext by hand.
 */
@SpringBootTest
class MethodSecurityTest {

    @Autowired
    private EmployeeService service;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(String username, Long employeeId, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(new AuthenticatedUser(username, employeeId), null, "ROLE_" + role));
    }

    @Test
    void noAuthentication_isRefusedByTheServiceItself() {
        assertThatThrownBy(() -> service.findAll()).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employee_cannotChangeSalaries() {
        assertThatThrownBy(() -> service.updateSalary(7L, new BigDecimal("1"), "x"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "HR")
    void hr_canReadTheSalaryHistory_butNotDelete() {
        assertThat(service.salaryHistory(7L)).isNotNull();
        assertThatThrownBy(() -> service.delete(7L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void ownership_employeeReadsOwnSalary_butNotAColleagues() {
        loginAs("asha", 4L, "EMPLOYEE");
        assertThat(service.salary(4L).salary()).isEqualByComparingTo("95000");
        assertThatThrownBy(() -> service.salary(5L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void ownership_managerSeesDirectReport_butNotOthers() {
        loginAs("manoj", 3L, "MANAGER");
        assertThat(service.findById(4L).name()).isEqualTo("Asha Verma");
        assertThatThrownBy(() -> service.findById(6L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void hr_cannotChangeOwnSalary() {
        loginAs("hana", 2L, "HR");
        assertThatThrownBy(() -> service.updateSalary(2L, new BigDecimal("999999"), "hana"))
                .isInstanceOf(AccessDeniedException.class);
    }
}
