package com.springlab.support;

import java.util.regex.Pattern;

/**
 * Detects a reference to the database port, not the four digits themselves. The digits "3306" occur by chance in timestamps
 * (for example 2026-11-03T23:17:37.873306Z), in durations and in ids, which made a plain substring check fail one run in a few
 * thousand. A real leak looks like a host:port, a "port" setting or a URL, and all of those are matched here.
 */
public final class PortReferences {

    /**
     * host:3306 (a colon right after a host-like token), "port 3306", "port=3306", a JSON "port" key followed by 3306, and 3306/ as in a
     * URL path. A JSON number after some other key (a duration, a sequence number) and digits inside a longer number are not references.
     */
    private static final Pattern PORT_REFERENCE = Pattern.compile("(?i)port\\W{0,4}3306(?![0-9])|(?<=[a-z0-9_.\\]-]):3306(?![0-9])|(?<![0-9.])3306/");

    private PortReferences() {
    }

    public static boolean mentions(String text) {
        return PORT_REFERENCE.matcher(text).find();
    }
}
