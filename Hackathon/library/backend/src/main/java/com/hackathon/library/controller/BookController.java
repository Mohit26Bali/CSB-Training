package com.hackathon.library.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.library.dto.BookResponse;
import com.hackathon.library.dto.LoanResponse;
import com.hackathon.library.dto.ReserveBody;
import com.hackathon.library.entity.LoanStatus;
import com.hackathon.library.service.LibraryService;

import jakarta.validation.Valid;

/** Member endpoints: search the catalogue, reserve, follow and cancel own reservations. */
@RestController
@RequestMapping("/api")
public class BookController {

    private final LibraryService library;

    public BookController(LibraryService library) {
        this.library = library;
    }

    @GetMapping("/books")
    public List<BookResponse> search(@RequestParam(required = false) String search, @RequestParam(required = false) String category,
                                     @RequestParam(defaultValue = "false") boolean availableOnly, Authentication authentication) {
        return library.search(authentication.getName(), search, category, availableOnly);
    }

    @GetMapping("/books/categories")
    public List<String> categories() {
        return library.categories();
    }

    @PostMapping("/loans")
    public ResponseEntity<LoanResponse> reserve(@Valid @RequestBody ReserveBody body, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(library.reserve(authentication.getName(), body.bookId()));
    }

    @GetMapping("/loans/my")
    public List<LoanResponse> mine(@RequestParam(required = false) LoanStatus status, Authentication authentication) {
        return library.myLoans(authentication.getName(), status);
    }

    @DeleteMapping("/loans/{id}")
    public LoanResponse cancel(@PathVariable Long id, Authentication authentication) {
        return library.cancel(authentication.getName(), id);
    }
}
