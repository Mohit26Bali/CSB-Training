package com.hackathon.referral.controller;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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

import com.hackathon.referral.entity.Job;
import com.hackathon.referral.entity.Role;
import com.hackathon.referral.entity.User;
import com.hackathon.referral.repo.JobRepository;
import com.hackathon.referral.repo.ReferralRepository;
import com.hackathon.referral.repo.UserRepository;
import com.hackathon.referral.security.JwtService;

@SpringBootTest
@ActiveProfiles("test")
class ApiSecurityTest {

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired JobRepository jobs;
    @Autowired ReferralRepository referrals;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;

    MockMvc mvc;
    String recruiter;
    String employee;
    String other;
    long jobId;
    long draftId;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        referrals.deleteAll();
        jobs.deleteAll();
        users.deleteAll();
        users.save(new User("rec", encoder.encode("recruiter123"), "Recruiter", Role.RECRUITER));
        users.save(new User("emp", encoder.encode("employee123"), "Emp", Role.EMPLOYEE));
        users.save(new User("other", encoder.encode("employee123"), "Other", Role.EMPLOYEE));
        Job published = new Job("Java Developer", "Engineering", "Pune", "Build things", List.of("Java", "Spring"), "rec");
        published.publish();
        jobId = jobs.save(published).getId();
        draftId = jobs.save(new Job("Architect", "Engineering", "Pune", "Not yet", List.of("AWS"), "rec")).getId();
        recruiter = "Bearer " + jwt.generateToken("rec", "RECRUITER");
        employee = "Bearer " + jwt.generateToken("emp", "EMPLOYEE");
        other = "Bearer " + jwt.generateToken("other", "EMPLOYEE");
    }

    private String referralJson(long job, String name, String email) {
        return String.format("{\"jobId\":%d,\"candidateName\":\"%s\",\"candidateEmail\":\"%s\",\"notes\":\"Great\"}", job, name, email);
    }

    private long refer(String token, long job, String name, String email) throws Exception {
        String body = mvc.perform(post("/api/referrals").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(referralJson(job, name, email))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void anonymousIs401() throws Exception {
        mvc.perform(get("/api/jobs")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void everyoneBrowsesPublishedJobsButRolesAreSeparated() throws Exception {
        mvc.perform(get("/api/jobs").header("Authorization", employee))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].title").value("Java Developer"));
        mvc.perform(get("/api/jobs?skill=spring").header("Authorization", recruiter)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/jobs?skill=aws").header("Authorization", employee)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/jobs/skills").header("Authorization", employee)).andExpect(jsonPath("$[0]").value("Java"));
        mvc.perform(get("/api/recruiter/jobs").header("Authorization", employee)).andExpect(status().isForbidden());
        mvc.perform(get("/api/recruiter/dashboard").header("Authorization", employee)).andExpect(status().isForbidden());
        mvc.perform(get("/api/referrals/my").header("Authorization", recruiter)).andExpect(status().isForbidden());
        mvc.perform(post("/api/referrals").header("Authorization", recruiter).contentType(MediaType.APPLICATION_JSON)
                .content(referralJson(jobId, "A", "a@x.com"))).andExpect(status().isForbidden());
    }

    @Test
    void referralValidationDuplicatesAndClosedJobs() throws Exception {
        mvc.perform(post("/api/referrals").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.jobId").value("Choose a job"))
                .andExpect(jsonPath("$.fieldErrors.candidateName").value("Candidate name is required"))
                .andExpect(jsonPath("$.fieldErrors.candidateEmail").value("Candidate email is required"));
        mvc.perform(post("/api/referrals").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON)
                .content("{\"jobId\":" + jobId + ",\"candidateName\":\"A\",\"candidateEmail\":\"nope\",\"candidatePhone\":\"abc\",\"resumeUrl\":\"ftp://x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.candidateEmail").value("Email is not valid"))
                .andExpect(jsonPath("$.fieldErrors.candidatePhone").exists())
                .andExpect(jsonPath("$.fieldErrors.resumeUrl").exists());

        refer(employee, jobId, "Asha", "asha@example.com");
        mvc.perform(post("/api/referrals").header("Authorization", other).contentType(MediaType.APPLICATION_JSON)
                .content(referralJson(jobId, "Asha", "ASHA@example.com"))).andExpect(status().isConflict());
        mvc.perform(post("/api/referrals").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON)
                .content(referralJson(draftId, "Bala", "bala@example.com"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not open")));
    }

    @Test
    void employeesSeeOnlyTheirOwnReferrals() throws Exception {
        refer(employee, jobId, "Asha", "asha@example.com");
        mvc.perform(get("/api/referrals/my").header("Authorization", employee))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$[0].allowedNext[0]").value("SCREENING"));
        mvc.perform(get("/api/referrals/my").header("Authorization", other)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void recruiterMovesAReferralThroughThePipeline() throws Exception {
        long id = refer(employee, jobId, "Asha", "asha@example.com");
        String path = "/api/recruiter/referrals/" + id + "/status";
        mvc.perform(put(path).header("Authorization", employee).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SCREENING\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put(path).header("Authorization", recruiter).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INTERVIEW\"}"))
                .andExpect(status().isConflict());
        mvc.perform(put(path).header("Authorization", recruiter).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.status").exists());
        mvc.perform(put(path).header("Authorization", recruiter).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"HIRED\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(path).header("Authorization", recruiter).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"screening\",\"note\":\"Phone screen\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SCREENING"))
                .andExpect(jsonPath("$.updatedBy").value("rec")).andExpect(jsonPath("$.statusNote").value("Phone screen"));
        mvc.perform(get("/api/recruiter/referrals?status=SCREENING").header("Authorization", recruiter))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].referrerName").value("Emp"));
        mvc.perform(get("/api/recruiter/dashboard").header("Authorization", recruiter))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalReferrals").value(1)).andExpect(jsonPath("$.byStatus.length()").value(5));
    }

    @Test
    void recruiterManagesJobs() throws Exception {
        String body = "{\"title\":\"QA Engineer\",\"department\":\"Quality\",\"location\":\"Remote\",\"description\":\"Test things\",\"skills\":[\"Selenium\",\"Java\"]}";
        String created = mvc.perform(post("/api/recruiter/jobs").header("Authorization", recruiter).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT")).andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));
        mvc.perform(post("/api/recruiter/jobs").header("Authorization", recruiter).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\",\"skills\":[]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.title").exists()).andExpect(jsonPath("$.fieldErrors.skills").exists());
        mvc.perform(put("/api/recruiter/jobs/" + id + "/publish").header("Authorization", recruiter))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(put("/api/recruiter/jobs/" + id + "/publish").header("Authorization", recruiter)).andExpect(status().isConflict());
        mvc.perform(get("/api/jobs").header("Authorization", employee)).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(put("/api/recruiter/jobs/" + id + "/close").header("Authorization", recruiter)).andExpect(status().isOk());
        mvc.perform(get("/api/jobs").header("Authorization", employee)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(put("/api/recruiter/jobs/" + id + "/publish").header("Authorization", employee)).andExpect(status().isForbidden());
        mvc.perform(get("/api/recruiter/jobs?status=CLOSED").header("Authorization", recruiter)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void registrationAlwaysCreatesAnEmployee() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"newbie\",\"password\":\"password1\",\"fullname\":\"New B\",\"role\":\"RECRUITER\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("EMPLOYEE"));
    }
}
