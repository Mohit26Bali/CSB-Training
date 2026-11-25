package com.csb.banking.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;


/**
 * End-to-end API test through the real filter chain (JWT filter, URL rules, @PreAuthorize, validation, exception
 * handler) against in-memory H2. Covers: login / register, 401 and 403 behaviour, account ownership, and the
 * account-opening rules. Fixture data comes from {@link AbstractApiTest}; money movement is tested in
 * {@link TransactionApiTest}.
 */
class AccountApiTest extends AbstractApiTest {

    @Test
    void requestsWithoutOrWithBadTokenGetJson401() throws Exception {
        mvc.perform(get("/api/accounts/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/api/accounts/me").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotUseAdminApi() throws Exception {
        mvc.perform(get("/api/admin/accounts").header("Authorization", raviToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mvc.perform(get("/api/admin/accounts").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void loginReturnsTokenAndWrongPasswordIs401() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ravi\",\"password\":\"customer123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ravi\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void registrationCreatesCustomerAndRejectsDuplicatesAndBadInput() throws Exception {
        String body = """
                {"username":"asha","password":"Violet-Rain-123!","fullName":"Asha Rao",
                 "email":"asha@example.com","mobile":"9000000001","address":"1 Lake Road"}""";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());

        String invalid = """
                {"username":"x","password":"short","fullName":"","email":"nope","mobile":"123","address":"a"}""";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.mobile").exists());
    }

    private String registrationWith(String username, String email, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"fullName\":\"Asha Rao\","
                + "\"email\":\"" + email + "\",\"mobile\":\"9000000001\",\"address\":\"1 Lake Road\"}";
    }

    @Test
    void weakPasswordsAreRejectedWithAMessageSayingWhatIsMissing() throws Exception {
        String[][] cases = {
                { "alllowercase", "password must include an uppercase letter, a digit and a symbol such as ! @ # or -" },
                { "NoSymbolHere1", "password must include a symbol such as ! @ # or -" },
                { "Gooood-Day-1", "password must not repeat the same character more than 3 times in a row" },
                { "Password1!", "password is too common; choose something harder to guess" },
                { "Crestline-1", "password is too common; choose something harder to guess" },
        };
        for (String[] c : cases) {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(registrationWith("asha", "asha@example.com", c[0])))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.password").value(c[1]));
        }
    }

    @Test
    void aPasswordLongerThan20CharactersIsRejectedAndOneOfExactly20IsAccepted() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registrationWith("asha", "asha@example.com", "Sunset-Rain-42!abcdef"))) // 21 characters
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").value("password must be 8 to 20 characters"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registrationWith("asha", "asha@example.com", "Sunset-Rain-42!abcde"))) // exactly 20
                .andExpect(status().isCreated());
    }

    @Test
    void aPasswordContainingTheUsernameOrEmailNameIsRejectedAndLoginOfOldWeakAccountsStillWorks() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registrationWith("meera.k", "someone@example.com", "My-meera.k-pass9!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("password must not contain your username"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registrationWith("someone", "Ravikumar@example.com", "Mine-RAVIKUMAR-9!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("password must not contain your email name"));

        // the seeded demo customer's password (customer123) would fail the new rules, but signing in is not checked against them
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ravi\",\"password\":\"customer123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void customerOpensAccountWithInitialDepositAndSeesItInTheirList() throws Exception {
        mvc.perform(post("/api/accounts").header("Authorization", priyaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\":\"CURRENT\",\"initialDeposit\":1500.50}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.balance").value(1500.50))
                .andExpect(jsonPath("$.customerName").value("Priya Sharma"))
                .andExpect(jsonPath("$.accountNumber").isNotEmpty());
        mvc.perform(get("/api/accounts/me").header("Authorization", priyaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void accountCreationValidatesInputAndIsCustomerOnly() throws Exception {
        mvc.perform(post("/api/accounts").header("Authorization", priyaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\":\"SAVINGS\",\"initialDeposit\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.initialDeposit").exists());
        mvc.perform(post("/api/accounts").header("Authorization", priyaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\":\"FIXED\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/accounts").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\":\"SAVINGS\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerSeesOnlyOwnAccountsButAdminSeesAll() throws Exception {
        mvc.perform(get("/api/accounts/" + raviAccountNumber).header("Authorization", raviToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(50000.00));
        mvc.perform(get("/api/accounts/" + raviAccountNumber + "/balance").header("Authorization", raviToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(50000.00));
        // Another customer gets 404, exactly as if the account did not exist.
        mvc.perform(get("/api/accounts/" + raviAccountNumber).header("Authorization", priyaToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/accounts/9999999999").header("Authorization", raviToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/accounts/" + raviAccountNumber).header("Authorization", adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void inactiveAccountHidesBalanceAndAdminCanReactivate() throws Exception {
        mvc.perform(patch("/api/admin/accounts/" + raviAccountNumber + "/status")
                        .header("Authorization", adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));
        mvc.perform(get("/api/accounts/" + raviAccountNumber + "/balance").header("Authorization", raviToken))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(patch("/api/admin/accounts/" + raviAccountNumber + "/status")
                        .header("Authorization", raviToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/accounts/" + raviAccountNumber + "/status")
                        .header("Authorization", adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/accounts/" + raviAccountNumber + "/balance").header("Authorization", raviToken))
                .andExpect(status().isOk());
    }
}
