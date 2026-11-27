package com.cartnest.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cartnest.dto.AddressRequest;
import com.cartnest.dto.AddressResponse;
import com.cartnest.security.SecurityUser;
import com.cartnest.service.AddressService;
import com.cartnest.service.IndianStates;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/addresses")
public class AddressController {
    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public List<AddressResponse> list(@AuthenticationPrincipal SecurityUser user) {
        return addressService.list(user.getId());
    }

    @GetMapping("/states")
    public List<String> states() {
        return IndianStates.ALL;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse create(@AuthenticationPrincipal SecurityUser user,
                                  @Valid @RequestBody AddressRequest request) {
        return addressService.create(user.getId(), request);
    }

    @PutMapping("/{id}")
    public AddressResponse update(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id,
                                  @Valid @RequestBody AddressRequest request) {
        return addressService.update(user.getId(), id, request);
    }

    @PutMapping("/{id}/default")
    public AddressResponse makeDefault(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id) {
        return addressService.setDefault(user.getId(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id) {
        addressService.delete(user.getId(), id);
    }
}
