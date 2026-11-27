package com.hackathon.expense.controller;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.hackathon.expense.entity.Expense;
import com.hackathon.expense.entity.ExpenseCategory;
import com.hackathon.expense.entity.Role;
import com.hackathon.expense.entity.User;
import com.hackathon.expense.repo.ExpenseRepository;
import com.hackathon.expense.repo.UserRepository;
import com.hackathon.expense.security.JwtService;

@SpringBootTest
@ActiveProfiles("test")
class ApiSecurityTest {

    @Autowired WebApplicationContext context;
    @Autowired UserRepository userRepository;
    @Autowired ExpenseRepository expenseRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    MockMvc mvc;
    String managerToken;
    String employeeToken;
    String otherEmployeeToken;
    Long otherEmployeesExpenseId;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        expenseRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(new User("manager", passwordEncoder.encode("manager123"), "Manager", Role.MANAGER));
        userRepository.save(new User("emp", passwordEncoder.encode("employee123"), "Emp", Role.EMPLOYEE));
        User other = userRepository.save(new User("other", passwordEncoder.encode("employee123"), "Other", Role.EMPLOYEE));
        otherEmployeesExpenseId = expenseRepository.save(new Expense(other, LocalDate.now(), ExpenseCategory.MEALS,
                new java.math.BigDecimal("99.00"), "Someone else's lunch")).getId();
        managerToken = "Bearer " + jwtService.generateToken("manager", "MANAGER");
        employeeToken = "Bearer " + jwtService.generateToken("emp", "EMPLOYEE");
        otherEmployeeToken = "Bearer " + jwtService.generateToken("other", "EMPLOYEE");
    }

    private static String expenseJson(String date, String category, String amount, String description) {
        return String.format("{\"expenseDate\":\"%s\",\"category\":\"%s\",\"amount\":%s,\"description\":\"%s\"}",
                date, category, amount, description);
    }

    @Test
    void anonymousRequestsGetJson401() throws Exception {
        mvc.perform(get("/api/expenses/my")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/api/manager/expenses").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void employeesCannotReachManagerApis() throws Exception {
        mvc.perform(get("/api/manager/expenses").header("Authorization", employeeToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/manager/dashboard").header("Authorization", employeeToken))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/manager/expenses/" + otherEmployeesExpenseId + "/approve")
                .header("Authorization", employeeToken)).andExpect(status().isForbidden());
    }

    @Test
    void managersCannotUseEmployeeApis() throws Exception {
        mvc.perform(get("/api/expenses/my").header("Authorization", managerToken)).andExpect(status().isForbidden());
        mvc.perform(post("/api/expenses").header("Authorization", managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(expenseJson(LocalDate.now().toString(), "MEALS", "10", "x"))).andExpect(status().isForbidden());
    }

    @Test
    void anEmployeeNeverSeesAnotherEmployeesExpenses() throws Exception {
        mvc.perform(get("/api/expenses/my").header("Authorization", employeeToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/expenses/my").header("Authorization", otherEmployeeToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void validExpenseIsCreated() throws Exception {
        mvc.perform(post("/api/expenses").header("Authorization", employeeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(expenseJson(LocalDate.now().toString(), "TRAVEL", "1234.50", "Taxi to client")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.employeeUsername").value("emp"));
    }

    @Test
    void invalidExpensesListEachBadField() throws Exception {
        mvc.perform(post("/api/expenses").header("Authorization", employeeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(expenseJson(LocalDate.now().toString(), "", "-5", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.category").value("Category is required"))
                .andExpect(jsonPath("$.fieldErrors.amount").value("Amount must be greater than zero"))
                .andExpect(jsonPath("$.fieldErrors.description").value("Description is required"));
        mvc.perform(post("/api/expenses").header("Authorization", employeeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(expenseJson(LocalDate.now().plusDays(3).toString(), "MEALS", "10", "Future lunch")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.expenseDate").value("Expense date cannot be in the future"));
        mvc.perform(post("/api/expenses").header("Authorization", employeeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(expenseJson(LocalDate.now().toString(), "MEALS", "10.999", "Too precise")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());
    }

    @Test
    void managerRejectsWithAReasonAndOnlyOnce() throws Exception {
        String path = "/api/manager/expenses/" + otherEmployeesExpenseId;
        mvc.perform(put(path + "/reject").header("Authorization", managerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reason").exists());
        mvc.perform(put(path + "/reject").header("Authorization", managerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Missing receipt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Missing receipt"));
        mvc.perform(put(path + "/approve").header("Authorization", managerToken))
                .andExpect(status().isConflict());
    }

    @Test
    void loginAndRegistration() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"manager\",\"password\":\"manager123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("MANAGER"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"manager\",\"password\":\"nope\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"newbie\",\"password\":\"password1\",\"fullname\":\"New B\",\"role\":\"MANAGER\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("EMPLOYEE"));
    }
}
