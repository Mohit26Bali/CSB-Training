package com.hackathon.vehicle.controller;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

import com.hackathon.vehicle.entity.Center;
import com.hackathon.vehicle.entity.Role;
import com.hackathon.vehicle.entity.ServiceType;
import com.hackathon.vehicle.entity.User;
import com.hackathon.vehicle.entity.Vehicle;
import com.hackathon.vehicle.entity.VehicleType;
import com.hackathon.vehicle.repo.CenterRepository;
import com.hackathon.vehicle.repo.ServiceBookingRepository;
import com.hackathon.vehicle.repo.ServiceTypeRepository;
import com.hackathon.vehicle.repo.UserRepository;
import com.hackathon.vehicle.repo.VehicleRepository;
import com.hackathon.vehicle.security.JwtService;

@SpringBootTest
@ActiveProfiles("test")
class ApiSecurityTest {

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired VehicleRepository vehicles;
    @Autowired ServiceTypeRepository services;
    @Autowired ServiceBookingRepository bookings;
    @Autowired CenterRepository centers;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;

    MockMvc mvc;
    String admin;
    String customer;
    String other;
    long vehicleId;
    long serviceId;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        bookings.deleteAll();
        vehicles.deleteAll();
        services.deleteAll();
        centers.deleteAll();
        users.deleteAll();
        centers.save(new Center(3));
        users.save(new User("adm", encoder.encode("admin123"), "Admin", Role.SERVICE_ADMIN));
        User c = users.save(new User("cust", encoder.encode("customer123"), "Customer", Role.CUSTOMER));
        users.save(new User("other", encoder.encode("customer123"), "Other", Role.CUSTOMER));
        vehicleId = vehicles.save(new Vehicle(c, "MH12AB1111", VehicleType.CAR, "Maruti", "Swift", 2019)).getId();
        serviceId = services.save(new ServiceType("Basic service", "Oil and checks", 1, new BigDecimal("2000.00"), true)).getId();
        admin = "Bearer " + jwt.generateToken("adm", "SERVICE_ADMIN");
        customer = "Bearer " + jwt.generateToken("cust", "CUSTOMER");
        other = "Bearer " + jwt.generateToken("other", "CUSTOMER");
    }

    private long idOf(String json) {
        return Long.parseLong(json.replaceAll(".*?\"id\":(\\d+).*", "$1"));
    }

    private String booking(long vehicle, int offset) {
        return "{\"vehicleId\":" + vehicle + ",\"serviceTypeId\":" + serviceId + ",\"serviceDate\":\"" + LocalDate.now().plusDays(offset) + "\"}";
    }

    private long book(String token, int offset) throws Exception {
        return idOf(mvc.perform(post("/api/bookings").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(booking(vehicleId, offset)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void anonymousIs401() throws Exception {
        mvc.perform(get("/api/catalog/services")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void rolesAreSeparated() throws Exception {
        mvc.perform(get("/api/admin/bookings").header("Authorization", customer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/dashboard").header("Authorization", customer)).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/bookings/1/start").header("Authorization", customer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/vehicles").header("Authorization", admin)).andExpect(status().isForbidden());
        mvc.perform(post("/api/bookings").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(booking(vehicleId, 3))).andExpect(status().isForbidden());
        mvc.perform(get("/api/catalog/services").header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get("/api/catalog/services").header("Authorization", customer)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void vehicleValidationShowsEveryProblem() throws Exception {
        mvc.perform(post("/api/vehicles").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.number").value("Vehicle number is required"))
                .andExpect(jsonPath("$.fieldErrors.type").value("Vehicle type is required"))
                .andExpect(jsonPath("$.fieldErrors.year").value("Year is required"));
        mvc.perform(post("/api/vehicles").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"number\":\"HELLO\",\"type\":\"CAR\",\"make\":\"A\",\"model\":\"B\",\"year\":1980}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.number").value("Use the format MH12AB1234"))
                .andExpect(jsonPath("$.fieldErrors.year").value("Year must be 1990 or later"));
    }

    @Test
    void registeringAndDuplicateNumbers() throws Exception {
        String body = "{\"number\":\"mh 14 cd 7777\",\"type\":\"BIKE\",\"make\":\"TVS\",\"model\":\"Apache\",\"year\":2022}";
        mvc.perform(post("/api/vehicles").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.number").value("MH14CD7777"));
        mvc.perform(post("/api/vehicles").header("Authorization", other).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.number").value(org.hamcrest.Matchers.containsString("already registered")));
        mvc.perform(get("/api/vehicles").header("Authorization", customer)).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/vehicles").header("Authorization", other)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void bookingValidationOwnershipAndOverlap() throws Exception {
        mvc.perform(post("/api/bookings").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.vehicleId").value("Choose a vehicle"))
                .andExpect(jsonPath("$.fieldErrors.serviceDate").value("Service date is required"));
        mvc.perform(post("/api/bookings").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON).content(booking(vehicleId, -2)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.serviceDate").value("Service date cannot be in the past"));
        mvc.perform(post("/api/bookings").header("Authorization", other).contentType(MediaType.APPLICATION_JSON).content(booking(vehicleId, 3)))
                .andExpect(status().isForbidden());
        long id = book(customer, 4);
        mvc.perform(post("/api/bookings").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON).content(booking(vehicleId, 4)))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/bookings/my?scope=upcoming").header("Authorization", customer)).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("BOOKED")).andExpect(jsonPath("$[0].estimatedCost").value(2000.0));
        mvc.perform(get("/api/bookings/my").header("Authorization", other)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(delete("/api/bookings/" + id).header("Authorization", other)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/bookings/" + id + "?reason=Busy").header("Authorization", customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED")).andExpect(jsonPath("$.cancelReason").value("Busy"));
        mvc.perform(delete("/api/bookings/" + id).header("Authorization", customer)).andExpect(status().isConflict());
        mvc.perform(delete("/api/bookings/999999").header("Authorization", customer)).andExpect(status().isNotFound());
    }

    @Test
    void theStatusFlowIsForTheGarage() throws Exception {
        long today = book(customer, 0);
        long later = book(customer, 6);
        mvc.perform(put("/api/admin/bookings/" + later + "/start").header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/bookings/" + today + "/complete").header("Authorization", admin)).andExpect(status().isConflict());
        mvc.perform(put("/api/admin/bookings/" + today + "/start").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_SERVICE")).andExpect(jsonPath("$.startedBy").value("adm"));
        mvc.perform(put("/api/admin/bookings/" + today + "/complete").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"finalCost\":-5}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.finalCost").value("Final cost cannot be negative"));
        mvc.perform(put("/api/admin/bookings/" + today + "/complete").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"finalCost\":2300,\"workNotes\":\"Oil changed\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.finalCost").value(2300.0));
        mvc.perform(put("/api/admin/bookings/" + later + "/cancel").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.reason").exists());
        mvc.perform(put("/api/admin/bookings/" + later + "/cancel").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"No parts\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cancelledBy").value("adm"));
        mvc.perform(get("/api/vehicles/" + vehicleId + "/history").header("Authorization", customer)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/vehicles/" + vehicleId + "/history").header("Authorization", other)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/vehicles/" + vehicleId + "/history").header("Authorization", admin)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void availabilityAndTheCatalogueManagement() throws Exception {
        book(customer, 2);
        mvc.perform(get("/api/catalog/availability?days=3").header("Authorization", customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3)).andExpect(jsonPath("$[2].booked").value(1)).andExpect(jsonPath("$[2].free").value(2));
        mvc.perform(get("/api/catalog/availability?days=0").header("Authorization", customer))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.days").exists());
        mvc.perform(post("/api/admin/services").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"durationDays\":9,\"basePrice\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").value("Name is required"))
                .andExpect(jsonPath("$.fieldErrors.durationDays").value("At most 5 days"))
                .andExpect(jsonPath("$.fieldErrors.basePrice").value("Price must be at least 100"));
        mvc.perform(post("/api/admin/services").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Tyre rotation\",\"durationDays\":1,\"basePrice\":900}")).andExpect(status().isCreated());
        mvc.perform(post("/api/admin/services").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"basic SERVICE\",\"durationDays\":1,\"basePrice\":900}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/services").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\",\"durationDays\":1,\"basePrice\":900}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/bays").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"bays\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.bays").value("At least 1 bay"));
        mvc.perform(put("/api/admin/bays").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"bays\":6}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bays").value(6));
    }
}
