package com.springlab.lab.micro.run;

import com.springlab.exception.InvalidRequestException;

import java.util.List;

/**
 * One safe What-If control an experiment offers. The browser can only pick a value allowed here: a boolean, an integer inside
 * [min, max] or one of the listed choices. Nothing else is ever accepted from the client.
 */
public record WhatIfOption(String key, String label, String kind, String defaultValue, Integer min, Integer max, List<String> choices) {

    public static WhatIfOption bool(String key, String label, boolean defaultValue) {
        return new WhatIfOption(key, label, "BOOLEAN", String.valueOf(defaultValue), null, null, List.of());
    }

    public static WhatIfOption integer(String key, String label, int defaultValue, int min, int max) {
        return new WhatIfOption(key, label, "INT", String.valueOf(defaultValue), min, max, List.of());
    }

    public static WhatIfOption choice(String key, String label, String defaultValue, List<String> choices) {
        return new WhatIfOption(key, label, "CHOICE", defaultValue, null, null, choices);
    }

    /** The canonical form of {@code raw}, or an InvalidRequestException that names the option but never echoes the value. */
    public String validate(String raw) {
        switch (kind) {
            case "BOOLEAN":
                if ("true".equals(raw) || "false".equals(raw)) {
                    return raw;
                }
                break;
            case "INT":
                try {
                    int v = Integer.parseInt(raw);
                    if (v >= min && v <= max) {
                        return String.valueOf(v);
                    }
                } catch (NumberFormatException ignored) {
                    // fall through to the error below
                }
                break;
            default:
                if (choices.contains(raw)) {
                    return raw;
                }
        }
        throw new InvalidRequestException("The what-if value for '" + key + "' is not allowed");
    }
}
