package com.cartnest.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cartnest.dto.HomeResponse;
import com.cartnest.dto.SuggestResponse;
import com.cartnest.service.CatalogSearchService;
import com.cartnest.service.HomeService;

@RestController
@RequestMapping("/api")
public class HomeController {
    private final HomeService homeService;
    private final CatalogSearchService searchService;

    public HomeController(HomeService homeService, CatalogSearchService searchService) {
        this.homeService = homeService;
        this.searchService = searchService;
    }

    @GetMapping("/home")
    public HomeResponse home() {
        return homeService.getHome();
    }

    @GetMapping("/search/suggest")
    public SuggestResponse suggest(@RequestParam(defaultValue = "") String q) {
        return searchService.suggest(q);
    }
}
