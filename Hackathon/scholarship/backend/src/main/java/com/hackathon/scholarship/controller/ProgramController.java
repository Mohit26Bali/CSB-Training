package com.hackathon.scholarship.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.scholarship.dto.ProgramResponse;
import com.hackathon.scholarship.service.ProgramService;

/** Any signed-in user can browse the active scholarships. */
@RestController
@RequestMapping("/api/programs")
public class ProgramController {

    private final ProgramService service;

    public ProgramController(ProgramService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProgramResponse> list() {
        return service.listActive();
    }
}
