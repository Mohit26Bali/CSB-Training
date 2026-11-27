package com.cartnest.controller.admin;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cartnest.dto.admin.ActiveRequest;
import com.cartnest.dto.admin.AdminCouponResponse;
import com.cartnest.dto.admin.AdminCustomerRow;
import com.cartnest.dto.admin.CouponRequest;
import com.cartnest.dto.admin.DashboardResponse;
import com.cartnest.dto.admin.EnabledRequest;
import com.cartnest.dto.admin.PageResponse;
import com.cartnest.service.AdminCouponService;
import com.cartnest.service.AdminCustomerService;
import com.cartnest.service.DashboardService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final DashboardService dashboardService;
    private final AdminCouponService couponService;
    private final AdminCustomerService customerService;

    public AdminController(DashboardService dashboardService, AdminCouponService couponService,
                           AdminCustomerService customerService) {
        this.dashboardService = dashboardService;
        this.couponService = couponService;
        this.customerService = customerService;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        return dashboardService.getDashboard();
    }

    @GetMapping("/coupons")
    public List<AdminCouponResponse> coupons() {
        return couponService.list();
    }

    @PostMapping("/coupons")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminCouponResponse createCoupon(@Valid @RequestBody CouponRequest request) {
        return couponService.create(request);
    }

    @PutMapping("/coupons/{id}")
    public AdminCouponResponse updateCoupon(@PathVariable Long id, @Valid @RequestBody CouponRequest request) {
        return couponService.update(id, request);
    }

    @PatchMapping("/coupons/{id}/active")
    public AdminCouponResponse setCouponActive(@PathVariable Long id, @Valid @RequestBody ActiveRequest request) {
        return couponService.setActive(id, request.active());
    }

    @GetMapping("/customers")
    public PageResponse<AdminCustomerRow> customers(@RequestParam(required = false) String q,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return customerService.list(q, page, size);
    }

    @PatchMapping("/customers/{id}/enabled")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setCustomerEnabled(@PathVariable Long id, @Valid @RequestBody EnabledRequest request) {
        customerService.setEnabled(id, request.enabled());
    }
}
