package com.hackathon.referral.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.referral.dto.ReferralRequest;
import com.hackathon.referral.dto.ReferralResponse;
import com.hackathon.referral.entity.ReferralStatus;
import com.hackathon.referral.service.ReferralService;

import jakarta.validation.Valid;

/** Employee endpoints: an employee only ever creates and sees their own referrals. */
@RestController
@RequestMapping("/api/referrals")
public class ReferralController {

    private final ReferralService service;

    public ReferralController(ReferralService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ReferralResponse> refer(@Valid @RequestBody ReferralRequest body, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.refer(authentication.getName(), body));
    }

    @GetMapping("/my")
    public List<ReferralResponse> mine(@RequestParam(required = false) ReferralStatus status, Authentication authentication) {
        return service.myReferrals(authentication.getName(), status);
    }
}
