package com.hackathon.scholarship.controller;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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

import com.hackathon.scholarship.entity.Role;
import com.hackathon.scholarship.entity.ScholarshipProgram;
import com.hackathon.scholarship.entity.User;
import com.hackathon.scholarship.repo.ApplicationRepository;
import com.hackathon.scholarship.repo.ScholarshipProgramRepository;
import com.hackathon.scholarship.repo.UserRepository;
import com.hackathon.scholarship.security.JwtService;

@SpringBootTest
@ActiveProfiles("test")
class ApiSecurityTest {

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired ScholarshipProgramRepository programs;
    @Autowired ApplicationRepository applications;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;

    MockMvc mvc;
    String admin;
    String student;
    String other;
    long programId;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        applications.deleteAll();
        programs.deleteAll();
        users.deleteAll();
        users.save(new User("admin", encoder.encode("admin123"), "Admin", Role.SCHOLARSHIP_ADMIN));
        users.save(new User("stu", encoder.encode("student123"), "Stu", Role.STUDENT));
        users.save(new User("other", encoder.encode("student123"), "Other", Role.STUDENT));
        programId = programs.save(new ScholarshipProgram("Merit", "Top grades", new BigDecimal("50000"),
                LocalDate.now().plusDays(10), new BigDecimal("8.00"), new BigDecimal("1000000"))).getId();
        admin = "Bearer " + jwt.generateToken("admin", "SCHOLARSHIP_ADMIN");
        student = "Bearer " + jwt.generateToken("stu", "STUDENT");
        other = "Bearer " + jwt.generateToken("other", "STUDENT");
    }

    private String applyJson(String gpa, String income, String course, String year, String statement) {
        return String.format("{\"programId\":%d,\"gpa\":%s,\"annualFamilyIncome\":%s,\"course\":\"%s\",\"yearOfStudy\":%s,\"statement\":\"%s\"}",
                programId, gpa, income, course, year, statement);
    }

    private long apply(String token) throws Exception {
        String body = mvc.perform(post("/api/applications").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(applyJson("9.0", "250000", "B.Tech", "2", "Please consider me"))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void anonymousIs401() throws Exception {
        mvc.perform(get("/api/programs")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void everyoneCanBrowseButRolesAreSeparated() throws Exception {
        mvc.perform(get("/api/programs").header("Authorization", student)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].open").value(true));
        mvc.perform(get("/api/programs").header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/applications").header("Authorization", student)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/statistics").header("Authorization", student)).andExpect(status().isForbidden());
        mvc.perform(get("/api/applications/my").header("Authorization", admin)).andExpect(status().isForbidden());
        mvc.perform(post("/api/applications").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(applyJson("9", "1", "X", "1", "Y"))).andExpect(status().isForbidden());
    }

    @Test
    void applicationShowsScoreAndIsPrivateToTheStudent() throws Exception {
        long id = apply(student);
        mvc.perform(get("/api/applications/my").header("Authorization", student))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].eligibilityScore").value(84)).andExpect(jsonPath("$[0].meetsCriteria").value(true))
                .andExpect(jsonPath("$[0].status").value("SUBMITTED"));
        mvc.perform(get("/api/applications/my").header("Authorization", other)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(put("/api/admin/applications/" + id + "/approve").header("Authorization", student)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"me\"}")).andExpect(status().isForbidden());
    }

    @Test
    void applyingTwiceIsAConflict() throws Exception {
        apply(student);
        mvc.perform(post("/api/applications").header("Authorization", student).contentType(MediaType.APPLICATION_JSON)
                .content(applyJson("9", "100", "B.Tech", "2", "Again"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You have already applied for 'Merit'"));
    }

    @Test
    void mandatoryEligibilityFieldsAreReportedPerField() throws Exception {
        mvc.perform(post("/api/applications").header("Authorization", student).contentType(MediaType.APPLICATION_JSON)
                .content("{\"programId\":" + programId + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.gpa").value("CGPA is required"))
                .andExpect(jsonPath("$.fieldErrors.annualFamilyIncome").value("Annual family income is required"))
                .andExpect(jsonPath("$.fieldErrors.course").value("Course of study is required"))
                .andExpect(jsonPath("$.fieldErrors.yearOfStudy").value("Year of study is required"))
                .andExpect(jsonPath("$.fieldErrors.statement").value("Please explain why you are applying"));
        mvc.perform(post("/api/applications").header("Authorization", student).contentType(MediaType.APPLICATION_JSON)
                .content(applyJson("11", "-5", "B.Tech", "9", "x")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.gpa").value("CGPA cannot be above 10"))
                .andExpect(jsonPath("$.fieldErrors.annualFamilyIncome").value("Income cannot be negative"))
                .andExpect(jsonPath("$.fieldErrors.yearOfStudy").value("Year of study must be between 1 and 6"));
    }

    @Test
    void adminDecidesWithAReasonAndOnlyOnce() throws Exception {
        long id = apply(student);
        String path = "/api/admin/applications/" + id;
        mvc.perform(put(path + "/approve").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.reason").exists());
        mvc.perform(put(path + "/approve").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Strong record\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decisionReason").value("Strong record")).andExpect(jsonPath("$.decidedBy").value("admin"));
        mvc.perform(put(path + "/reject").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Oops\"}")).andExpect(status().isConflict());
        mvc.perform(get("/api/admin/statistics").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.approved").value(1))
                .andExpect(jsonPath("$.totalAwarded").value(50000.0)).andExpect(jsonPath("$.byProgram.length()").value(1));
    }

    @Test
    void adminManagesProgrammes() throws Exception {
        String body = "{\"name\":\"Arts Prize\",\"description\":\"For artists\",\"amount\":20000,\"deadline\":\"%s\",\"minGpa\":6.5,\"maxFamilyIncome\":700000}";
        mvc.perform(post("/api/admin/programs").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(String.format(body, LocalDate.now().plusDays(20))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.open").value(true));
        mvc.perform(post("/api/admin/programs").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(String.format(body, LocalDate.now().plusDays(20)).replace("Arts Prize", "merit")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.name").exists());
        mvc.perform(post("/api/admin/programs").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"amount\":0,\"minGpa\":11}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.deadline").exists()).andExpect(jsonPath("$.fieldErrors.minGpa").exists());
        mvc.perform(get("/api/admin/programs").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(post("/api/admin/programs").header("Authorization", student).contentType(MediaType.APPLICATION_JSON)
                .content(String.format(body, LocalDate.now().plusDays(20)))).andExpect(status().isForbidden());
    }

    @Test
    void registrationAlwaysCreatesAStudent() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"newbie\",\"password\":\"password1\",\"fullname\":\"New B\",\"role\":\"SCHOLARSHIP_ADMIN\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("STUDENT"));
    }
}
