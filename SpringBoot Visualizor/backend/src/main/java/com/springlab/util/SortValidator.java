package com.springlab.util;

import com.springlab.exception.InvalidRequestException;
import org.springframework.data.domain.Pageable;

import java.util.Set;
import java.util.TreeSet;

public final class SortValidator {

    private SortValidator() {
    }

    /** Rejects sorting by anything outside the whitelist, so clients cannot order by internal or sensitive columns. */
    public static void requireAllowed(Pageable pageable, Set<String> allowed) {
        pageable.getSort().forEach(order -> {
            if (!allowed.contains(order.getProperty())) {
                throw new InvalidRequestException("Cannot sort by '" + order.getProperty()
                        + "'. Allowed: " + String.join(", ", new TreeSet<>(allowed)));
            }
        });
    }
}
