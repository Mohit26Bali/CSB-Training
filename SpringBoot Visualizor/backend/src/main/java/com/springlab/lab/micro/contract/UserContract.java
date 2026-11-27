package com.springlab.lab.micro.contract;

import com.springlab.lab.micro.core.Operation;
import com.springlab.lab.micro.core.ServiceName;

import java.math.BigDecimal;

/** The public contract of the User service. The business refusal code is NOT_FOUND. */
public final class UserContract {

    private UserContract() {
    }

    /** Lab fixture, not part of a real service API: creates a customer. */
    public static final Operation<SeedUser, UserRef> SEED_USER = new Operation<>(ServiceName.USER, "seedUser");
    public static final Operation<GetUser, UserView> GET_USER = new Operation<>(ServiceName.USER, "getUser");

    public record SeedUser(String name, BigDecimal creditLimit) {
    }

    public record UserRef(long userRef) {
    }

    public record GetUser(long userRef) {
    }

    public record UserView(long userRef, String name, BigDecimal creditLimit) {
    }
}
