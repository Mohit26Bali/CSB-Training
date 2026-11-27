package com.hackathon.library.controller;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.hackathon.library.entity.Book;
import com.hackathon.library.entity.Role;
import com.hackathon.library.entity.User;
import com.hackathon.library.repo.BookRepository;
import com.hackathon.library.repo.LoanRepository;
import com.hackathon.library.repo.UserRepository;
import com.hackathon.library.security.JwtService;

@SpringBootTest
@ActiveProfiles("test")
class ApiSecurityTest {

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired BookRepository books;
    @Autowired LoanRepository loans;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;

    MockMvc mvc;
    String librarian;
    String member;
    String other;
    long bookId;
    long scarceId;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        loans.deleteAll();
        books.deleteAll();
        users.deleteAll();
        users.save(new User("lib", encoder.encode("librarian123"), "Librarian", Role.LIBRARIAN));
        users.save(new User("mem", encoder.encode("member123"), "Mem", Role.MEMBER));
        users.save(new User("other", encoder.encode("member123"), "Other", Role.MEMBER));
        bookId = books.save(new Book("Effective Java", "Joshua Bloch", "9780134685991", "Technology", "Tips", 2018, 2)).getId();
        scarceId = books.save(new Book("Rare Book", "Someone", "9780135957059", "History", null, 1900, 1)).getId();
        librarian = "Bearer " + jwt.generateToken("lib", "LIBRARIAN");
        member = "Bearer " + jwt.generateToken("mem", "MEMBER");
        other = "Bearer " + jwt.generateToken("other", "MEMBER");
    }

    private long idOf(String json) {
        return Long.parseLong(json.replaceAll(".*?\"id\":(\\d+).*", "$1"));
    }

    private long reserve(String token, long book) throws Exception {
        return idOf(mvc.perform(post("/api/loans").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":" + book + "}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void anonymousIs401() throws Exception {
        mvc.perform(get("/api/books")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void rolesAreSeparated() throws Exception {
        mvc.perform(get("/api/librarian/loans").header("Authorization", member)).andExpect(status().isForbidden());
        mvc.perform(get("/api/librarian/dashboard").header("Authorization", member)).andExpect(status().isForbidden());
        mvc.perform(post("/api/librarian/books").header("Authorization", member).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/loans").header("Authorization", librarian).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":" + bookId + "}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/loans/my").header("Authorization", librarian)).andExpect(status().isForbidden());
        mvc.perform(get("/api/books").header("Authorization", member)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/books").header("Authorization", librarian)).andExpect(status().isOk());
        mvc.perform(get("/api/books/categories").header("Authorization", member)).andExpect(jsonPath("$[0]").value("History"));
    }

    @Test
    void searchFiltersWork() throws Exception {
        mvc.perform(get("/api/books?search=bloch").header("Authorization", member)).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].availableCopies").value(2));
        mvc.perform(get("/api/books?category=history").header("Authorization", member)).andExpect(jsonPath("$[0].title").value("Rare Book"));
        long id = reserve(member, scarceId);
        mvc.perform(get("/api/books?availableOnly=true").header("Authorization", other)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/books?search=rare").header("Authorization", member))
                .andExpect(jsonPath("$[0].myLoanStatus").value("RESERVED")).andExpect(jsonPath("$[0].myLoanId").value(id));
    }

    @Test
    void reserveIssueAndReturnEndToEnd() throws Exception {
        mvc.perform(post("/api/loans").header("Authorization", member).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.bookId").value("Choose a book"));
        long id = reserve(member, scarceId);
        mvc.perform(post("/api/loans").header("Authorization", member).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":" + scarceId + "}")).andExpect(status().isConflict());
        mvc.perform(post("/api/loans").header("Authorization", other).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":" + scarceId + "}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not available")));

        mvc.perform(get("/api/librarian/loans?status=RESERVED").header("Authorization", librarian)).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].memberName").value("Mem"));
        mvc.perform(put("/api/librarian/loans/" + id + "/return").header("Authorization", librarian)).andExpect(status().isConflict());
        mvc.perform(put("/api/librarian/loans/" + id + "/issue").header("Authorization", member)).andExpect(status().isForbidden());
        mvc.perform(put("/api/librarian/loans/" + id + "/issue").header("Authorization", librarian)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ISSUED")).andExpect(jsonPath("$.dueDate").exists()).andExpect(jsonPath("$.overdue").value(false));
        mvc.perform(put("/api/librarian/loans/" + id + "/issue").header("Authorization", librarian)).andExpect(status().isConflict());
        mvc.perform(get("/api/loans/my?status=ISSUED").header("Authorization", member)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/librarian/dashboard").header("Authorization", librarian)).andExpect(jsonPath("$.issued").value(1))
                .andExpect(jsonPath("$.available").value(2));

        mvc.perform(put("/api/librarian/loans/" + id + "/return").header("Authorization", librarian)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED")).andExpect(jsonPath("$.fine").value(0));
        mvc.perform(put("/api/librarian/loans/" + id + "/return").header("Authorization", librarian)).andExpect(status().isConflict());
        mvc.perform(put("/api/librarian/loans/999999/issue").header("Authorization", librarian)).andExpect(status().isNotFound());
        reserve(other, scarceId); // the copy is back on the shelf
    }

    @Test
    void cancelIsOwnerOnly() throws Exception {
        long id = reserve(member, bookId);
        mvc.perform(delete("/api/loans/" + id).header("Authorization", other)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/loans/999999").header("Authorization", member)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/loans/" + id).header("Authorization", member)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(delete("/api/loans/" + id).header("Authorization", member)).andExpect(status().isConflict());
    }

    @Test
    void bookValidationAndDuplicates() throws Exception {
        mvc.perform(post("/api/librarian/books").header("Authorization", librarian).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").value("Title is required"))
                .andExpect(jsonPath("$.fieldErrors.totalCopies").value("Number of copies is required"));
        mvc.perform(post("/api/librarian/books").header("Authorization", librarian).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"T\",\"author\":\"A\",\"isbn\":\"123\",\"category\":\"Fiction\",\"totalCopies\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.isbn").value("Enter a valid 10 or 13 digit ISBN"))
                .andExpect(jsonPath("$.fieldErrors.totalCopies").value("There must be at least 1 copy"));
        mvc.perform(post("/api/librarian/books").header("Authorization", librarian).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"T\",\"author\":\"A\",\"isbn\":\"978-0-13-468599-1\",\"category\":\"Fiction\",\"totalCopies\":1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.isbn").value(org.hamcrest.Matchers.containsString("already exists")));
        mvc.perform(post("/api/librarian/books").header("Authorization", librarian).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"New\",\"author\":\"A\",\"isbn\":\"9780441172719\",\"category\":\"Fiction\",\"totalCopies\":3}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.availableCopies").value(3));
    }
}
