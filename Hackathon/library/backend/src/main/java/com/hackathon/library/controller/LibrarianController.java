package com.hackathon.library.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.library.dto.BookBody;
import com.hackathon.library.dto.BookResponse;
import com.hackathon.library.dto.LibraryDashboard;
import com.hackathon.library.dto.LoanResponse;
import com.hackathon.library.entity.LoanStatus;
import com.hackathon.library.service.LibraryService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/librarian")
public class LibrarianController {

    private final LibraryService library;

    public LibrarianController(LibraryService library) {
        this.library = library;
    }

    @GetMapping("/loans")
    public List<LoanResponse> loans(@RequestParam(required = false) LoanStatus status, @RequestParam(required = false) String search) {
        return library.allLoans(status, search);
    }

    @PutMapping("/loans/{id}/issue")
    public LoanResponse issue(@PathVariable Long id, Authentication authentication) {
        return library.issue(id, authentication.getName());
    }

    @PutMapping("/loans/{id}/return")
    public LoanResponse giveBack(@PathVariable Long id, Authentication authentication) {
        return library.giveBack(id, authentication.getName());
    }

    @PostMapping("/books")
    public ResponseEntity<BookResponse> createBook(@Valid @RequestBody BookBody body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(library.createBook(body));
    }

    @PutMapping("/books/{id}")
    public BookResponse updateBook(@PathVariable Long id, @Valid @RequestBody BookBody body) {
        return library.updateBook(id, body);
    }

    @GetMapping("/dashboard")
    public LibraryDashboard dashboard() {
        return library.dashboard();
    }
}
