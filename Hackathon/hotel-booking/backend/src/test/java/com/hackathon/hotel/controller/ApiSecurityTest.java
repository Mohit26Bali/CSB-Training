package com.hackathon.hotel.controller;

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

import com.hackathon.hotel.entity.Role;
import com.hackathon.hotel.entity.Room;
import com.hackathon.hotel.entity.RoomType;
import com.hackathon.hotel.entity.User;
import com.hackathon.hotel.repo.BookingRepository;
import com.hackathon.hotel.repo.RoomRepository;
import com.hackathon.hotel.repo.UserRepository;
import com.hackathon.hotel.security.JwtService;

@SpringBootTest
@ActiveProfiles("test")
class ApiSecurityTest {

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired RoomRepository rooms;
    @Autowired BookingRepository bookings;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;

    MockMvc mvc;
    String admin;
    String guest;
    String other;
    long roomId;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        bookings.deleteAll();
        rooms.deleteAll();
        users.deleteAll();
        users.save(new User("adm", encoder.encode("admin123"), "Admin", Role.HOTEL_ADMIN));
        users.save(new User("guest", encoder.encode("customer123"), "Guest", Role.CUSTOMER));
        users.save(new User("other", encoder.encode("customer123"), "Other", Role.CUSTOMER));
        roomId = rooms.save(new Room("101", RoomType.STANDARD, 1, new BigDecimal("3000.00"), 2, "Cosy", "Wi-Fi")).getId();
        admin = "Bearer " + jwt.generateToken("adm", "HOTEL_ADMIN");
        guest = "Bearer " + jwt.generateToken("guest", "CUSTOMER");
        other = "Bearer " + jwt.generateToken("other", "CUSTOMER");
    }

    private long idOf(String json) {
        return Long.parseLong(json.replaceAll(".*?\"id\":(\\d+).*", "$1"));
    }

    private String stay(long room, int inOffset, int outOffset, int guests) {
        return "{\"roomId\":" + room + ",\"checkIn\":\"" + LocalDate.now().plusDays(inOffset) + "\",\"checkOut\":\"" + LocalDate.now().plusDays(outOffset) + "\",\"guests\":" + guests + "}";
    }

    private long book(String token, int inOffset, int outOffset) throws Exception {
        return idOf(mvc.perform(post("/api/bookings").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(stay(roomId, inOffset, outOffset, 2)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void anonymousIs401() throws Exception {
        mvc.perform(get("/api/rooms/types")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void rolesAreSeparated() throws Exception {
        mvc.perform(get("/api/admin/rooms").header("Authorization", guest)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/dashboard").header("Authorization", guest)).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/bookings/1/confirm").header("Authorization", guest)).andExpect(status().isForbidden());
        mvc.perform(post("/api/bookings").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(stay(roomId, 3, 4, 1))).andExpect(status().isForbidden());
        mvc.perform(get("/api/bookings/my").header("Authorization", admin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/rooms/types").header("Authorization", guest)).andExpect(jsonPath("$[0]").value("STANDARD"));
    }

    @Test
    void searchValidationAndResults() throws Exception {
        LocalDate in = LocalDate.now().plusDays(5);
        mvc.perform(get("/api/rooms/available?checkIn=" + in + "&checkOut=" + in.plusDays(2)).header("Authorization", guest))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].nights").value(2)).andExpect(jsonPath("$[0].totalPrice").value(6000.0));
        mvc.perform(get("/api/rooms/available?checkIn=" + in + "&checkOut=" + in).header("Authorization", guest))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.checkOut").value("Check-out must be after check-in"));
        mvc.perform(get("/api/rooms/available?checkIn=" + LocalDate.now().minusDays(1) + "&checkOut=" + in).header("Authorization", guest))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.checkIn").value("Check-in cannot be in the past"));
        mvc.perform(get("/api/rooms/available").header("Authorization", guest)).andExpect(status().isBadRequest());
    }

    @Test
    void bookingValidationAndOwnership() throws Exception {
        mvc.perform(post("/api/bookings").header("Authorization", guest).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.roomId").value("Choose a room"))
                .andExpect(jsonPath("$.fieldErrors.checkIn").value("Check-in date is required"))
                .andExpect(jsonPath("$.fieldErrors.guests").value("Number of guests is required"));
        mvc.perform(post("/api/bookings").header("Authorization", guest).contentType(MediaType.APPLICATION_JSON).content(stay(roomId, 3, 4, 5)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.guests").value(org.hamcrest.Matchers.containsString("sleeps at most")));
        long id = book(guest, 5, 8);
        mvc.perform(post("/api/bookings").header("Authorization", guest).contentType(MediaType.APPLICATION_JSON).content(stay(roomId, 6, 7, 1))).andExpect(status().isConflict());
        mvc.perform(get("/api/bookings/my").header("Authorization", guest)).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDING")).andExpect(jsonPath("$[0].nights").value(3)).andExpect(jsonPath("$[0].totalPrice").value(9000.0));
        mvc.perform(get("/api/bookings/my").header("Authorization", other)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(delete("/api/bookings/" + id).header("Authorization", other)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/bookings/999999").header("Authorization", guest)).andExpect(status().isNotFound());
    }

    @Test
    void confirmReleasesRivalsAndConfirmedStaysBlockSearchAndBooking() throws Exception {
        long mine = book(guest, 5, 8);
        long rival = book(other, 6, 9);
        mvc.perform(put("/api/admin/bookings/" + mine + "/confirm").header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED")).andExpect(jsonPath("$.autoCancelled").value(1));
        mvc.perform(get("/api/bookings/my").header("Authorization", other)).andExpect(jsonPath("$[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$[0].cancelReason").value(org.hamcrest.Matchers.containsString("another guest")));
        mvc.perform(put("/api/admin/bookings/" + rival + "/confirm").header("Authorization", admin)).andExpect(status().isConflict());
        mvc.perform(put("/api/admin/bookings/" + mine + "/confirm").header("Authorization", admin)).andExpect(status().isConflict());
        LocalDate in = LocalDate.now().plusDays(6);
        mvc.perform(get("/api/rooms/available?checkIn=" + in + "&checkOut=" + in.plusDays(1)).header("Authorization", guest)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post("/api/bookings").header("Authorization", other).contentType(MediaType.APPLICATION_JSON).content(stay(roomId, 6, 7, 1))).andExpect(status().isConflict());
        mvc.perform(get("/api/admin/calendar").header("Authorization", admin)).andExpect(status().isOk()).andExpect(jsonPath("$.rooms.length()").value(1));
        mvc.perform(get("/api/admin/dashboard").header("Authorization", admin)).andExpect(jsonPath("$.rooms").value(1));
    }

    @Test
    void adminCancelNeedsAReasonAndGuestCancelWorksBeforeCheckIn() throws Exception {
        long id = book(guest, 5, 8);
        mvc.perform(put("/api/admin/bookings/" + id + "/cancel").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.reason").value("A reason is required when the hotel cancels a booking"));
        mvc.perform(put("/api/admin/bookings/" + id + "/cancel").header("Authorization", guest).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
        long second = book(other, 10, 12);
        mvc.perform(delete("/api/bookings/" + second + "?reason=Plans changed").header("Authorization", other)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED")).andExpect(jsonPath("$.cancelReason").value("Plans changed"));
        mvc.perform(delete("/api/bookings/" + second).header("Authorization", other)).andExpect(status().isConflict());
        mvc.perform(put("/api/admin/bookings/" + id + "/cancel").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Maintenance\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cancelledBy").value("adm"));
        mvc.perform(get("/api/admin/bookings?status=CANCELLED").header("Authorization", admin)).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void roomManagement() throws Exception {
        mvc.perform(post("/api/admin/rooms").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.number").value("Room number is required"))
                .andExpect(jsonPath("$.fieldErrors.type").value("Choose a room type"))
                .andExpect(jsonPath("$.fieldErrors.pricePerNight").value("Price per night is required"));
        mvc.perform(post("/api/admin/rooms").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"number\":\"202\",\"type\":\"deluxe\",\"floor\":2,\"pricePerNight\":50,\"capacity\":2}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.pricePerNight").value("Price must be at least 100"));
        mvc.perform(post("/api/admin/rooms").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"number\":\"101\",\"type\":\"deluxe\",\"floor\":2,\"pricePerNight\":5000,\"capacity\":2}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.number").value("Room 101 already exists"));
        mvc.perform(post("/api/admin/rooms").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"number\":\"202\",\"type\":\"deluxe\",\"floor\":2,\"pricePerNight\":5000,\"capacity\":3,\"amenities\":\"Wi-Fi, Minibar\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.amenities[1]").value("Minibar"));
        mvc.perform(get("/api/admin/rooms?type=deluxe").header("Authorization", admin)).andExpect(jsonPath("$.length()").value(1));
        book(guest, 5, 7);
        mvc.perform(put("/api/admin/rooms/" + roomId).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"number\":\"101\",\"type\":\"STANDARD\",\"floor\":1,\"pricePerNight\":3000,\"capacity\":2,\"active\":false}"))
                .andExpect(status().isBadRequest());
    }
}
