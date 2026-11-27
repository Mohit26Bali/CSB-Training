package com.hackathon.asset.controller;

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

import com.hackathon.asset.entity.Asset;
import com.hackathon.asset.entity.AssetCategory;
import com.hackathon.asset.entity.AssetCondition;
import com.hackathon.asset.entity.Role;
import com.hackathon.asset.entity.User;
import com.hackathon.asset.repo.AssetRepository;
import com.hackathon.asset.repo.AssignmentRepository;
import com.hackathon.asset.repo.UserRepository;
import com.hackathon.asset.security.JwtService;

@SpringBootTest
@ActiveProfiles("test")
class ApiSecurityTest {

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired AssetRepository assets;
    @Autowired AssignmentRepository assignments;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;

    MockMvc mvc;
    String admin;
    String employee;
    String other;
    long assetId;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        assignments.deleteAll();
        assets.deleteAll();
        users.deleteAll();
        users.save(new User("adm", encoder.encode("admin123"), "Admin", Role.ASSET_ADMIN));
        users.save(new User("emp", encoder.encode("employee123"), "Employee", Role.EMPLOYEE));
        users.save(new User("other", encoder.encode("employee123"), "Other", Role.EMPLOYEE));
        assetId = assets.save(new Asset("LT-1001", "LT00001111", "Dell Latitude", AssetCategory.LAPTOP, "Dell", null, null, new BigDecimal("80000"), AssetCondition.GOOD, null)).getId();
        admin = "Bearer " + jwt.generateToken("adm", "ASSET_ADMIN");
        employee = "Bearer " + jwt.generateToken("emp", "EMPLOYEE");
        other = "Bearer " + jwt.generateToken("other", "EMPLOYEE");
    }

    private long idOf(String json) {
        return Long.parseLong(json.replaceAll(".*?\"id\":(\\d+).*", "$1"));
    }

    private long assign(String username) throws Exception {
        return idOf(mvc.perform(post("/api/admin/assets/" + assetId + "/assign").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeUsername\":\"" + username + "\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void anonymousIs401() throws Exception {
        mvc.perform(get("/api/my/assets")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void rolesAreSeparated() throws Exception {
        mvc.perform(get("/api/admin/assets").header("Authorization", employee)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/dashboard").header("Authorization", employee)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/assets/" + assetId + "/assign").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeUsername\":\"emp\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/my/assets").header("Authorization", admin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/assets/meta").header("Authorization", employee)).andExpect(status().isOk()).andExpect(jsonPath("$.categories[0]").value("LAPTOP"));
    }

    @Test
    void assetValidationShowsEveryProblem() throws Exception {
        mvc.perform(post("/api/admin/assets").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.assetTag").value("Asset tag is required"))
                .andExpect(jsonPath("$.fieldErrors.serialNumber").value("Serial number is required"))
                .andExpect(jsonPath("$.fieldErrors.name").value("Name is required"))
                .andExpect(jsonPath("$.fieldErrors.category").value("Category is required"))
                .andExpect(jsonPath("$.fieldErrors.condition").value("Condition is required"));
        mvc.perform(post("/api/admin/assets").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"assetTag\":\"BAD\",\"serialNumber\":\"x\",\"name\":\"Thing\",\"category\":\"LAPTOP\",\"condition\":\"NEW\",\"purchasePrice\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.assetTag").value("Use a tag like LT-1001"))
                .andExpect(jsonPath("$.fieldErrors.serialNumber").value(org.hamcrest.Matchers.containsString("4 to 30")))
                .andExpect(jsonPath("$.fieldErrors.purchasePrice").value("Price cannot be negative"));
        mvc.perform(post("/api/admin/assets").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"assetTag\":\"lt-1001\",\"serialNumber\":\"ZZZ-9999\",\"name\":\"Thing\",\"category\":\"LAPTOP\",\"condition\":\"NEW\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.assetTag").value(org.hamcrest.Matchers.containsString("already in use")));
        mvc.perform(post("/api/admin/assets").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"assetTag\":\"mn-2001\",\"serialNumber\":\"zzz-9999\",\"name\":\"Monitor\",\"category\":\"MONITOR\",\"condition\":\"NEW\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.assetTag").value("MN-2001")).andExpect(jsonPath("$.serialNumber").value("ZZZ-9999"));
    }

    @Test
    void theWholeLifeOfAnAssignment() throws Exception {
        long id = assign("emp");
        mvc.perform(post("/api/admin/assets/" + assetId + "/assign").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeUsername\":\"other\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Only available assets")));
        mvc.perform(post("/api/admin/assets/" + assetId + "/assign").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.employeeUsername").value("Choose an employee"));
        mvc.perform(get("/api/my/assets").header("Authorization", employee)).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].assetTag").value("LT-1001"));
        mvc.perform(get("/api/my/assets").header("Authorization", other)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post("/api/my/assignments/" + id + "/return").header("Authorization", other)).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/assignments/" + id + "/accept").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"condition\":\"GOOD\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/my/assignments/" + id + "/return").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Not needed\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RETURN_REQUESTED"));
        mvc.perform(post("/api/my/assignments/" + id + "/return").header("Authorization", employee)).andExpect(status().isConflict());
        mvc.perform(get("/api/admin/assignments").header("Authorization", admin)).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].returnNote").value("Not needed"));
        mvc.perform(put("/api/admin/assignments/" + id + "/accept").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.condition").exists());
        mvc.perform(put("/api/admin/assignments/" + id + "/accept").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"condition\":\"FAIR\",\"note\":\"Scratched\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RETURNED")).andExpect(jsonPath("$.conditionIn").value("FAIR"));
        mvc.perform(get("/api/admin/assets/" + assetId).header("Authorization", admin)).andExpect(jsonPath("$.status").value("AVAILABLE")).andExpect(jsonPath("$.condition").value("FAIR"));
        mvc.perform(get("/api/my/history").header("Authorization", employee)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/admin/assets/" + assetId + "/history").header("Authorization", admin)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void searchStatusChangesAndTheDashboard() throws Exception {
        mvc.perform(get("/api/admin/assets?search=lt00001111").header("Authorization", admin)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/admin/assets?search=nothing").header("Authorization", admin)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/admin/assets?status=BOGUS").header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/assets/" + assetId + "/status").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.status").value("Choose a status"));
        mvc.perform(put("/api/admin/assets/" + assetId + "/status").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_REPAIR\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_REPAIR"));
        mvc.perform(put("/api/admin/assets/" + assetId + "/status").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_REPAIR\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/admin/employees?search=oth").header("Authorization", admin)).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].username").value("other"));
        mvc.perform(get("/api/admin/dashboard").header("Authorization", admin)).andExpect(status().isOk()).andExpect(jsonPath("$.totalAssets").value(1)).andExpect(jsonPath("$.inRepair").value(1));
        mvc.perform(get("/api/admin/assets/999999").header("Authorization", admin)).andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/assets/" + assetId + "/assign").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeUsername\":\"emp\",\"dueDate\":\"" + LocalDate.now() + "\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.dueDate").value("Due date must be after today"));
    }
}
