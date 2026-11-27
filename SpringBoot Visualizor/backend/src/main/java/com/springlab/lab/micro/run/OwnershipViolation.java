package com.springlab.lab.micro.run;

/** A service tried to use a table it does not own. The lab refuses the statement before it reaches MySQL. */
public final class OwnershipViolation extends RuntimeException {

    public OwnershipViolation(String message) {
        super(message);
    }
}
