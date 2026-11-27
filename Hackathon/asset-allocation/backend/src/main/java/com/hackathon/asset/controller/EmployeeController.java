package com.hackathon.asset.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.asset.dto.AssignmentResponse;
import com.hackathon.asset.dto.ReturnBody;
import com.hackathon.asset.entity.AssetCategory;
import com.hackathon.asset.entity.AssetCondition;
import com.hackathon.asset.service.AssetService;

import jakarta.validation.Valid;

/** Employee endpoints: the assets they hold, what they handed back, and return requests. Also the lists of categories and conditions for forms. */
@RestController
@RequestMapping("/api")
public class EmployeeController {

    private final AssetService service;

    public EmployeeController(AssetService service) {
        this.service = service;
    }

    @GetMapping("/assets/meta")
    public Map<String, List<String>> meta() {
        return Map.of("categories", java.util.Arrays.stream(AssetCategory.values()).map(Enum::name).toList(),
                "conditions", java.util.Arrays.stream(AssetCondition.values()).map(Enum::name).toList());
    }

    @GetMapping("/my/assets")
    public List<AssignmentResponse> mine(Authentication authentication) {
        return service.myAssets(authentication.getName());
    }

    @GetMapping("/my/history")
    public List<AssignmentResponse> history(Authentication authentication) {
        return service.myHistory(authentication.getName());
    }

    @PostMapping("/my/assignments/{id}/return")
    public AssignmentResponse requestReturn(@PathVariable Long id, @Valid @RequestBody(required = false) ReturnBody body, Authentication authentication) {
        return service.requestReturn(authentication.getName(), id, body == null ? null : body.note());
    }
}
