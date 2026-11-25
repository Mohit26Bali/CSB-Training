package com.cartnest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.cartnest.entity.User;
import com.cartnest.repository.UserRepository;

class AuthApiTests extends ApiTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Test
    void registrationCreatesALoggedInCustomer() throws Exception {
        String email = "New.Customer-" + UUID.randomUUID() + "@Example.com";
        String token = register(email);
        mvc.perform(as(token, get("/api/auth/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
        // The same email in another letter case is still taken
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"X\",\"email\":\"" + email.toUpperCase() + "\",\"password\":\"Secret123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An account with this email already exists"));
    }

    @Test
    void registrationValidatesEveryField() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\",\"email\":\"not-an-email\",\"password\":\"short\",\"phone\":\"12345\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fullName").value("Full name is required"))
                .andExpect(jsonPath("$.fieldErrors.email").value("Enter a valid email address"))
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.phone").value("Enter a 10-digit Indian mobile number"));
    }

    @Test
    void seededAccountsCanLogInAndWrongPasswordsCannot() throws Exception {
        String token = login("riya@example.com", "Customer@123");
        mvc.perform(as(token, get("/api/auth/me"))).andExpect(jsonPath("$.fullName").value("Riya Verma"));
        login("ADMIN@cartnest.in", "Admin@123");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"riya@example.com\",\"password\":\"wrong-password1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Incorrect email or password"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"whatever1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Incorrect email or password"));
    }

    @Test
    void fiveWrongPasswordsLockTheEmail() throws Exception {
        String email = "lock-" + UUID.randomUUID() + "@example.com";
        register(email);
        String wrong = "{\"email\":\"" + email + "\",\"password\":\"Wrong1234\"}";
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrong))
                    .andExpect(status().isUnauthorized());
        }
        // Locked now: even the right password is refused
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Too many failed attempts. Try again in 15 minute(s)"));
    }

    @Test
    void protectedEndpointsNeedAValidTokenAndTheRightRole() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Please log in to continue"));
        mvc.perform(get("/api/cart").header("Authorization", "Bearer not.a.token"))
                .andExpect(status().isUnauthorized());
        // The public catalogue works with no token, and even with a broken one
        mvc.perform(get("/api/products/iphone-13-pro").header("Authorization", "Bearer not.a.token"))
                .andExpect(status().isOk());

        String admin = login("admin@cartnest.in", "Admin@123");
        mvc.perform(as(admin, get("/api/cart")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have permission to do this"));
        mvc.perform(as(newCustomer(), get("/api/admin/anything"))).andExpect(status().isForbidden());
    }

    @Test
    void aDisabledAccountsTokenStopsWorking() throws Exception {
        String email = "disabled-" + UUID.randomUUID() + "@example.com";
        String token = register(email);
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setEnabled(false);
        userRepository.save(user);
        mvc.perform(as(token, get("/api/auth/me"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void profileAndPasswordCanBeChanged() throws Exception {
        String email = "account-" + UUID.randomUUID() + "@example.com";
        String token = register(email);
        mvc.perform(as(token, put("/api/account/profile")).content("{\"fullName\":\"Renamed User\",\"phone\":\"9876543210\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Renamed User"))
                .andExpect(jsonPath("$.phone").value("9876543210"));
        mvc.perform(as(token, put("/api/account/password"))
                        .content("{\"currentPassword\":\"Nope12345\",\"newPassword\":\"Better456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
        mvc.perform(as(token, put("/api/account/password"))
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"Better456\"}"))
                .andExpect(status().isNoContent());
        login(email, "Better456");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }
}
