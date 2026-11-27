package com.hackathon.expense.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.expense.dto.ExpenseResponse;
import com.hackathon.expense.dto.ManagerDashboard;
import com.hackathon.expense.dto.RejectRequest;
import com.hackathon.expense.entity.ExpenseStatus;
import com.hackathon.expense.service.ExpenseService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/manager")
public class ManagerExpenseController {

    private final ExpenseService expenseService;

    public ManagerExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @GetMapping("/expenses")
    public List<ExpenseResponse> list(@RequestParam(required = false) ExpenseStatus status,
                                      @RequestParam(required = false) String category,
                                      @RequestParam(required = false) String search) {
        return expenseService.allExpenses(status, category, search);
    }

    @PutMapping("/expenses/{id}/approve")
    public ExpenseResponse approve(@PathVariable Long id, Authentication authentication) {
        return expenseService.approve(id, authentication.getName());
    }

    @PutMapping("/expenses/{id}/reject")
    public ExpenseResponse reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request,
                                  Authentication authentication) {
        return expenseService.reject(id, request.reason(), authentication.getName());
    }

    @GetMapping("/dashboard")
    public ManagerDashboard dashboard() {
        return expenseService.dashboard();
    }
}
