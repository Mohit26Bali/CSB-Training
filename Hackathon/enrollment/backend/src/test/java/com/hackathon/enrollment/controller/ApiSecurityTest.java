package com.hackathon.enrollment.controller;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.hackathon.enrollment.entity.Course;
import com.hackathon.enrollment.entity.Role;
import com.hackathon.enrollment.entity.User;
import com.hackathon.enrollment.repo.CourseRepository;
import com.hackathon.enrollment.repo.EnrollmentRepository;
import com.hackathon.enrollment.repo.UserRepository;
import com.hackathon.enrollment.security.JwtService;

@SpringBootTest
@ActiveProfiles("test")
class ApiSecurityTest {

    @Autowired WebApplicationContext context;
    @Autowired UserRepository userRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    MockMvc mvc;
    String adminToken;
    String employeeToken;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        enrollmentRepository.deleteAll();
        courseRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(new User("admin", passwordEncoder.encode("admin123"), "Admin", Role.ADMIN));
        userRepository.save(new User("emp", passwordEncoder.encode("employee123"), "Emp", Role.EMPLOYEE));
        courseRepository.save(new Course("Java", "d", "Cat", 2));
        adminToken = "Bearer " + jwtService.generateToken("admin", "ADMIN");
        employeeToken = "Bearer " + jwtService.generateToken("emp", "EMPLOYEE");
    }

    @Test
    void requestsWithoutTokenGetJson401() throws Exception {
        mvc.perform(get("/api/courses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/api/courses").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void employeesCannotUseAdminApisAndAdminsCannotEnroll() throws Exception {
        mvc.perform(get("/api/admin/courses").header("Authorization", employeeToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mvc.perform(get("/api/enrollments/my").header("Authorization", adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginReturnsTokenAndWrongPasswordIs401() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"emp\",\"password\":\"employee123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"emp\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registrationAlwaysCreatesAnEmployee() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"newbie\",\"password\":\"password1\",\"fullname\":\"New B\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));
    }

    @Test
    void validationErrorsListEveryBadField() throws Exception {
        mvc.perform(post("/api/admin/courses").header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\",\"category\":\"\",\"capacity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").value("Title is required"))
                .andExpect(jsonPath("$.fieldErrors.category").value("Category is required"))
                .andExpect(jsonPath("$.fieldErrors.capacity").value("Capacity must be at least 1"));
    }

    @Test
    void courseImageMustBeASitePathOrHttpUrl() throws Exception {
        String body = "{\"title\":\"Pic\",\"category\":\"Cat\",\"capacity\":5,\"imageUrl\":\"%s\"}";
        mvc.perform(post("/api/admin/courses").header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(String.format(body, "javascript:alert(1)")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.imageUrl").exists());
        mvc.perform(post("/api/admin/courses").header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(String.format(body, "/course-images/java-basics.jpg")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").value("/course-images/java-basics.jpg"));
    }

    @Test
    void deleteReturnsJsonAndUnknownEndpointsAre404() throws Exception {
        Long id = courseRepository.findAll().get(0).getId();
        mvc.perform(delete("/api/admin/courses/" + id).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Course deleted"));
        mvc.perform(get("/api/nothing-here").header("Authorization", employeeToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
