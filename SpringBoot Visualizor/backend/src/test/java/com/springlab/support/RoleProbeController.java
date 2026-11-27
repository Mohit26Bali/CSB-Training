package com.springlab.support;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only endpoints (lives in src/test, never shipped) used to exercise method security,
 * 401 vs 403 behaviour and the catch-all exception handler.
 */
@RestController
@RequestMapping("/api/test")
public class RoleProbeController {

    @GetMapping("/any")
    public String any() {
        return "ok";
    }

    @GetMapping("/teacher")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public String teacher() {
        return "teacher-or-admin";
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public String admin() {
        return "admin";
    }

    @GetMapping("/boom")
    public String boom() {
        throw new IllegalStateException("secret internal detail");
    }
}
