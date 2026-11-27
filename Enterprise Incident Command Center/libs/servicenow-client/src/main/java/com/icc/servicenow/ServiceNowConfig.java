package com.icc.servicenow;

import java.time.Duration;

/**
 * Where ServiceNow is and who we are. The values come from the environment (SERVICENOW_URL, SERVICENOW_USER,
 * SERVICENOW_PASSWORD), never from the repository. toString never shows the password.
 */
public record ServiceNowConfig(String baseUrl, String username, String password, Duration connectTimeout, Duration readTimeout) {

    public ServiceNowConfig(String baseUrl, String username, String password) {
        this(baseUrl, username, password, Duration.ofSeconds(5), Duration.ofSeconds(15));
    }

    @Override
    public String toString() {
        return "ServiceNowConfig[baseUrl=" + baseUrl + ", username=" + username + ", password=***]";
    }
}
