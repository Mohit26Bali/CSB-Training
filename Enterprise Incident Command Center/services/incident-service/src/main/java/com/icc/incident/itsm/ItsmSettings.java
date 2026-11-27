package com.icc.incident.itsm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Is the sync to ServiceNow switched on? One setting decides: icc.itsm.mode = local (the default: nothing is ever
 * sent, records stay LOCAL_ONLY) or servicenow. Environment variable: ICC_ITSM_MODE.
 */
@Component
public class ItsmSettings {

    private final String mode;

    public ItsmSettings(@Value("${icc.itsm.mode:local}") String mode) {
        this.mode = mode == null ? "local" : mode.trim().toLowerCase();
    }

    public boolean enabled() {
        return "servicenow".equals(mode);
    }

    public String mode() {
        return mode;
    }
}
