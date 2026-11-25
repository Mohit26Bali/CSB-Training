package com.cartnest;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

class AddressApiTests extends ApiTestSupport {

    private static String address(String city, String state, String pincode, boolean makeDefault) {
        return "{\"fullName\":\"Riya Verma\",\"phone\":\"9876543210\",\"line1\":\"12 MG Road\",\"city\":\"" + city
                + "\",\"state\":\"" + state + "\",\"pincode\":\"" + pincode + "\",\"type\":\"HOME\",\"makeDefault\":"
                + makeDefault + "}";
    }

    private Integer create(String token, String body) throws Exception {
        String json = mvc.perform(as(token, post("/api/addresses")).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    @Test
    void firstAddressIsTheDefaultAndOnlyOneDefaultExists() throws Exception {
        String token = newCustomer();
        Integer pune = create(token, address("Pune", "maharashtra", "411001", false));
        Integer delhi = create(token, address("New Delhi", "Delhi", "110001", true));

        mvc.perform(as(token, get("/api/addresses")))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(delhi))
                .andExpect(jsonPath("$[*].isDefault", contains(true, false)))
                // the state is stored with its official spelling
                .andExpect(jsonPath("$[1].state").value("Maharashtra"));

        mvc.perform(as(token, put("/api/addresses/" + pune + "/default"))).andExpect(status().isOk());
        mvc.perform(as(token, get("/api/addresses")))
                .andExpect(jsonPath("$[0].id").value(pune))
                .andExpect(jsonPath("$[*].isDefault", contains(true, false)));
    }

    @Test
    void deletingTheDefaultPromotesAnotherAddress() throws Exception {
        String token = newCustomer();
        Integer first = create(token, address("Pune", "Maharashtra", "411001", false));
        Integer second = create(token, address("Mumbai", "Maharashtra", "400001", false));
        mvc.perform(as(token, delete("/api/addresses/" + first))).andExpect(status().isNoContent());
        mvc.perform(as(token, get("/api/addresses")))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[0].isDefault").value(true));
    }

    @Test
    void addressesAreValidated() throws Exception {
        String token = newCustomer();
        mvc.perform(as(token, post("/api/addresses"))
                        .content("{\"fullName\":\"A\",\"phone\":\"12345\",\"line1\":\"\",\"city\":\"Pune\",\"state\":\"Maharashtra\","
                                + "\"pincode\":\"011001\",\"type\":\"HOME\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.phone").value("Enter a 10-digit Indian mobile number"))
                .andExpect(jsonPath("$.fieldErrors.line1").value("Address line 1 is required"))
                .andExpect(jsonPath("$.fieldErrors.pincode").value("Pincode must be 6 digits and cannot start with 0"));
        mvc.perform(as(token, post("/api/addresses")).content(address("Paris", "Ile-de-France", "750001", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("'Ile-de-France' is not an Indian state or union territory"));
        mvc.perform(as(token, post("/api/addresses")).content(address("Pune", "Maharashtra", "411001", false)
                        .replace("\"HOME\"", "\"CASTLE\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customersOnlySeeTheirOwnAddresses() throws Exception {
        String owner = newCustomer();
        Integer id = create(owner, address("Pune", "Maharashtra", "411001", false));
        String stranger = newCustomer();
        mvc.perform(as(stranger, get("/api/addresses"))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(as(stranger, put("/api/addresses/" + id)).content(address("Goa", "Goa", "403001", false)))
                .andExpect(status().isNotFound());
        mvc.perform(as(stranger, delete("/api/addresses/" + id))).andExpect(status().isNotFound());
    }

    @Test
    void atMostTenAddresses() throws Exception {
        String token = newCustomer();
        for (int i = 0; i < 10; i++) {
            create(token, address("Pune", "Maharashtra", "411001", false));
        }
        mvc.perform(as(token, post("/api/addresses")).content(address("Pune", "Maharashtra", "411001", false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You can save at most 10 addresses"));
    }
}
