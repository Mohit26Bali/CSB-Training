package com.hackathon.expense.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.expense.dto.ExpenseRequest;
import com.hackathon.expense.dto.ExpenseResponse;
import com.hackathon.expense.dto.StatusSummary;
import com.hackathon.expense.entity.ExpenseStatus;
import com.hackathon.expense.service.ExpenseService;

import jakarta.validation.Valid;

/** Employee endpoints: an employee only ever sees and creates their own expenses. */
@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @GetMapping("/categories")
    public List<Map<String, String>> categories() {
        return expenseService.categories();
    }

    @PostMapping
    public ResponseEntity<ExpenseResponse> submit(@Valid @RequestBody ExpenseRequest request,
                                                  Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(expenseService.submit(authentication.getName(), request));
    }

    @GetMapping("/my")
    public List<ExpenseResponse> mine(@RequestParam(required = false) ExpenseStatus status,
                                      @RequestParam(required = false) String category,
                                      @RequestParam(required = false) String search,
                                      Authentication authentication) {
        return expenseService.myExpenses(authentication.getName(), status, category, search);
    }

    @GetMapping("/my/summary")
    public StatusSummary mySummary(Authentication authentication) {
        return expenseService.mySummary(authentication.getName());
    }
}
