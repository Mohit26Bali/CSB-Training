package com.springlab.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Initial administrator, supplied only through environment variables (ADMIN_USERNAME / ADMIN_EMAIL / ADMIN_PASSWORD). */
@ConfigurationProperties(prefix = "springlab.bootstrap-admin")
public record BootstrapAdminProperties(String username, String email, String password) {

    @Override
    public String toString() {
        return "BootstrapAdminProperties[username=" + username + ", email=" + email + ", password=***]";
    }
}
