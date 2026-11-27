package com.hackathon.appointment.controller;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

import com.hackathon.appointment.entity.Provider;
import com.hackathon.appointment.entity.Role;
import com.hackathon.appointment.entity.Slot;
import com.hackathon.appointment.entity.User;
import com.hackathon.appointment.repo.AppointmentRepository;
import com.hackathon.appointment.repo.ProviderRepository;
import com.hackathon.appointment.repo.SlotRepository;
import com.hackathon.appointment.repo.UserRepository;
import com.hackathon.appointment.security.JwtService;

@SpringBootTest
@ActiveProfiles("test")
class ApiSecurityTest {

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired ProviderRepository providers;
    @Autowired SlotRepository slots;
    @Autowired AppointmentRepository appointments;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;

    MockMvc mvc;
    String provider;
    String otherProvider;
    String customer;
    String other;
    long providerId;
    long slotId;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        appointments.deleteAll();
        slots.deleteAll();
        providers.deleteAll();
        users.deleteAll();
        User p = users.save(new User("prov", encoder.encode("provider123"), "Dr. Prov", Role.SERVICE_PROVIDER));
        User p2 = users.save(new User("prov2", encoder.encode("provider123"), "Dr. Two", Role.SERVICE_PROVIDER));
        users.save(new User("cust", encoder.encode("customer123"), "Customer", Role.CUSTOMER));
        users.save(new User("other", encoder.encode("customer123"), "Other", Role.CUSTOMER));
        Provider saved = providers.save(new Provider(p, "Dentist", "Teeth", "Pune"));
        providers.save(new Provider(p2, "Physio", "Backs", "Mumbai"));
        providerId = saved.getId();
        LocalDateTime start = LocalDate.now().plusDays(2).atTime(10, 0);
        slotId = slots.save(new Slot(saved, start, start.plusMinutes(30))).getId();
        provider = "Bearer " + jwt.generateToken("prov", "SERVICE_PROVIDER");
        otherProvider = "Bearer " + jwt.generateToken("prov2", "SERVICE_PROVIDER");
        customer = "Bearer " + jwt.generateToken("cust", "CUSTOMER");
        other = "Bearer " + jwt.generateToken("other", "CUSTOMER");
    }

    private long idOf(String json) {
        return Long.parseLong(json.replaceAll(".*?\"id\":(\\d+).*", "$1"));
    }

    private long book(String token) throws Exception {
        return idOf(mvc.perform(post("/api/appointments").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"slotId\":" + slotId + ",\"reason\":\"Check-up\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void anonymousIs401() throws Exception {
        mvc.perform(get("/api/providers")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void rolesAreSeparated() throws Exception {
        mvc.perform(get("/api/provider/slots").header("Authorization", customer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/provider/dashboard").header("Authorization", customer)).andExpect(status().isForbidden());
        mvc.perform(put("/api/provider/appointments/1/confirm").header("Authorization", customer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/appointments/my").header("Authorization", provider)).andExpect(status().isForbidden());
        mvc.perform(post("/api/appointments").header("Authorization", provider).contentType(MediaType.APPLICATION_JSON).content("{\"slotId\":" + slotId + "}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/providers").header("Authorization", provider)).andExpect(status().isOk());
        mvc.perform(get("/api/providers").header("Authorization", customer)).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void customersSeeSlotsWithoutPersonalDetails() throws Exception {
        book(customer);
        mvc.perform(get("/api/providers/" + providerId + "/slots?days=5").header("Authorization", other))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].customerName").doesNotExist());
        mvc.perform(get("/api/provider/slots?from=" + LocalDate.now().plusDays(2) + "&days=1").header("Authorization", provider))
                .andExpect(jsonPath("$[0].customerName").value("Customer"));
        mvc.perform(get("/api/providers/" + providerId + "/slots?days=99").header("Authorization", other))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.days").exists());
        mvc.perform(get("/api/providers/999999/slots").header("Authorization", other)).andExpect(status().isNotFound());
    }

    @Test
    void bookingValidationOwnershipAndDoubleBooking() throws Exception {
        mvc.perform(post("/api/appointments").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.slotId").value("Choose a slot"));
        long id = book(customer);
        mvc.perform(post("/api/appointments").header("Authorization", other).contentType(MediaType.APPLICATION_JSON).content("{\"slotId\":" + slotId + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("just been taken")));
        mvc.perform(get("/api/appointments/my?scope=upcoming").header("Authorization", customer)).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDING")).andExpect(jsonPath("$[0].providerName").value("Dr. Prov"));
        mvc.perform(get("/api/appointments/my").header("Authorization", other)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(delete("/api/appointments/" + id).header("Authorization", other)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/appointments/" + id + "?reason=Busy").header("Authorization", customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED")).andExpect(jsonPath("$.cancelReason").value("Busy"));
        mvc.perform(delete("/api/appointments/" + id).header("Authorization", customer)).andExpect(status().isConflict());
        mvc.perform(delete("/api/appointments/999999").header("Authorization", customer)).andExpect(status().isNotFound());
    }

    @Test
    void theProviderDecides() throws Exception {
        long id = book(customer);
        mvc.perform(put("/api/provider/appointments/" + id + "/confirm").header("Authorization", otherProvider)).andExpect(status().isForbidden());
        mvc.perform(put("/api/provider/appointments/" + id + "/confirm").header("Authorization", provider))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED")).andExpect(jsonPath("$.confirmedBy").value("prov"));
        mvc.perform(put("/api/provider/appointments/" + id + "/confirm").header("Authorization", provider)).andExpect(status().isConflict());
        mvc.perform(put("/api/provider/appointments/" + id + "/cancel").header("Authorization", provider).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.reason").exists());
        mvc.perform(put("/api/provider/appointments/" + id + "/cancel").header("Authorization", provider).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Emergency\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cancelledBy").value("prov"));
        mvc.perform(get("/api/provider/appointments?status=CANCELLED").header("Authorization", provider)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/provider/appointments").header("Authorization", otherProvider)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void slotCreationValidation() throws Exception {
        String day = LocalDate.now().plusDays(3).toString();
        mvc.perform(post("/api/provider/slots").header("Authorization", provider).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.date").value("Date is required"))
                .andExpect(jsonPath("$.fieldErrors.startTime").value("Start time is required"))
                .andExpect(jsonPath("$.fieldErrors.minutes").value("Length is required"));
        mvc.perform(post("/api/provider/slots").header("Authorization", provider).contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"" + day + "\",\"startTime\":\"09:00\",\"minutes\":5}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.minutes").value("A slot is at least 15 minutes"));
        mvc.perform(post("/api/provider/slots").header("Authorization", provider).contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"" + day + "\",\"startTime\":\"09:00\",\"minutes\":30}")).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("AVAILABLE"));
        mvc.perform(post("/api/provider/slots").header("Authorization", provider).contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"" + day + "\",\"startTime\":\"09:15\",\"minutes\":30}")).andExpect(status().isConflict());
        mvc.perform(post("/api/provider/slots/bulk").header("Authorization", provider).contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"" + day + "\",\"fromTime\":\"10:00\",\"toTime\":\"12:00\",\"minutes\":30}")).andExpect(status().isCreated()).andExpect(jsonPath("$.length()").value(4));
        mvc.perform(post("/api/provider/slots").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"" + day + "\",\"startTime\":\"15:00\",\"minutes\":30}")).andExpect(status().isForbidden());
    }

    @Test
    void closingSlotsAndTheProfile() throws Exception {
        long id = book(customer);
        mvc.perform(delete("/api/provider/slots/" + slotId).header("Authorization", provider)).andExpect(status().isConflict());
        mvc.perform(delete("/api/provider/slots/" + slotId).header("Authorization", otherProvider)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/appointments/" + id).header("Authorization", customer)).andExpect(status().isOk());
        mvc.perform(delete("/api/provider/slots/" + slotId).header("Authorization", provider)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/provider/slots/" + slotId).header("Authorization", provider)).andExpect(status().isConflict());
        mvc.perform(put("/api/provider/profile").header("Authorization", provider).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.title").value("Title is required"));
        mvc.perform(put("/api/provider/profile").header("Authorization", provider).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Orthodontist\",\"location\":\"Pune\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Orthodontist"));
        mvc.perform(get("/api/provider/dashboard").header("Authorization", provider)).andExpect(status().isOk()).andExpect(jsonPath("$.days.length()").value(7));
    }
}
