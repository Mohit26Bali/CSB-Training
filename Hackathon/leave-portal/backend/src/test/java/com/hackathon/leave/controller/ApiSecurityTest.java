package com.hackathon.leave.controller;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;

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

import com.hackathon.leave.entity.Role;
import com.hackathon.leave.entity.User;
import com.hackathon.leave.repo.LeaveBalanceRepository;
import com.hackathon.leave.repo.LeaveRequestRepository;
import com.hackathon.leave.repo.UserRepository;
import com.hackathon.leave.security.JwtService;

@SpringBootTest
@ActiveProfiles("test")
class ApiSecurityTest {

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired LeaveRequestRepository requests;
    @Autowired LeaveBalanceRepository balances;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;

    static final LocalDate M = LocalDate.of(LocalDate.now().getYear(), Month.MARCH, 1)
            .with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY));

    MockMvc mvc;
    String manager;
    String manager2;
    String employee;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        requests.deleteAll();
        balances.deleteAll();
        users.deleteAll();
        users.save(new User("manager", encoder.encode("manager123"), "Manager", Role.MANAGER));
        users.save(new User("manager2", encoder.encode("manager123"), "Manager Two", Role.MANAGER));
        users.save(new User("emp", encoder.encode("employee123"), "Emp", Role.EMPLOYEE));
        manager = "Bearer " + jwt.generateToken("manager", "MANAGER");
        manager2 = "Bearer " + jwt.generateToken("manager2", "MANAGER");
        employee = "Bearer " + jwt.generateToken("emp", "EMPLOYEE");
    }

    private static String leaveJson(String type, LocalDate from, LocalDate to, String reason) {
        return String.format("{\"leaveType\":\"%s\",\"startDate\":\"%s\",\"endDate\":\"%s\",\"reason\":\"%s\"}",
                type, from, to, reason);
    }

    private long create(String token, String type, LocalDate from, LocalDate to) throws Exception {
        String body = mvc.perform(post("/api/leave").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(leaveJson(type, from, to, "Time off")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void anonymousIs401() throws Exception {
        mvc.perform(get("/api/leave/my")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void employeesCannotReachManagerApis() throws Exception {
        mvc.perform(get("/api/manager/leave").header("Authorization", employee)).andExpect(status().isForbidden());
        mvc.perform(get("/api/manager/leave/calendar").header("Authorization", employee)).andExpect(status().isForbidden());
        mvc.perform(put("/api/manager/leave/1/approve").header("Authorization", employee)).andExpect(status().isForbidden());
    }

    @Test
    void managersCanAlsoRequestLeaveForThemselves() throws Exception {
        create(manager, "ANNUAL", M, M.plusDays(1));
        mvc.perform(get("/api/leave/my").header("Authorization", manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void balanceListsEveryTypeWithAllowances() throws Exception {
        mvc.perform(get("/api/leave/balance").header("Authorization", employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].leaveType").value("ANNUAL"))
                .andExpect(jsonPath("$[0].allocated").value(18))
                .andExpect(jsonPath("$[0].available").value(18));
    }

    @Test
    void validationErrorsAreReportedPerField() throws Exception {
        mvc.perform(post("/api/leave").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON)
                .content("{\"leaveType\":\"\",\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.leaveType").value("Leave type is required"))
                .andExpect(jsonPath("$.fieldErrors.startDate").value("Start date is required"))
                .andExpect(jsonPath("$.fieldErrors.reason").value("Reason is required"));
        mvc.perform(post("/api/leave").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON)
                .content(leaveJson("ANNUAL", M.plusDays(2), M, "Backwards")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.endDate").value("End date cannot be before the start date"));
    }

    @Test
    void overlapIsAConflict() throws Exception {
        create(employee, "ANNUAL", M, M.plusDays(2));
        mvc.perform(post("/api/leave").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON)
                .content(leaveJson("SICK", M.plusDays(1), M.plusDays(3), "Overlap")))
                .andExpect(status().isConflict());
    }

    @Test
    void fullApprovalFlowWithBalanceAndOwnRequestProtection() throws Exception {
        long id = create(employee, "ANNUAL", M, M.plusDays(2));

        mvc.perform(put("/api/manager/leave/" + id + "/approve").header("Authorization", manager)
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Enjoy\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedBy").value("manager")).andExpect(jsonPath("$.decisionNote").value("Enjoy"));
        mvc.perform(get("/api/leave/balance").header("Authorization", employee))
                .andExpect(jsonPath("$[0].used").value(3)).andExpect(jsonPath("$[0].available").value(15));
        mvc.perform(put("/api/manager/leave/" + id + "/approve").header("Authorization", manager2))
                .andExpect(status().isConflict());

        long own = create(manager, "ANNUAL", M.plusDays(14), M.plusDays(14));
        mvc.perform(put("/api/manager/leave/" + own + "/approve").header("Authorization", manager))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/manager/leave/" + own + "/reject").header("Authorization", manager2))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void cancelAndCalendar() throws Exception {
        long id = create(employee, "ANNUAL", M, M.plusDays(1));
        mvc.perform(delete("/api/leave/" + id).header("Authorization", employee))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        long approved = create(employee, "ANNUAL", M.plusDays(7), M.plusDays(8));
        mvc.perform(put("/api/manager/leave/" + approved + "/approve").header("Authorization", manager))
                .andExpect(status().isOk());
        mvc.perform(get("/api/manager/leave/calendar?month=" + M.getYear() + "-03").header("Authorization", manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/manager/leave/calendar?month=nope").header("Authorization", manager))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.month").exists());
    }

    @Test
    void registrationAlwaysCreatesAnEmployee() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"newbie\",\"password\":\"password1\",\"fullname\":\"New B\",\"role\":\"MANAGER\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("EMPLOYEE"));
    }
}
