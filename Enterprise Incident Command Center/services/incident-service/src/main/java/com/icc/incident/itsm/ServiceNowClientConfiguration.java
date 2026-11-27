package com.icc.incident.itsm;

import com.icc.servicenow.ServiceNowClient;
import com.icc.servicenow.ServiceNowConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the ServiceNow client from the environment (SERVICENOW_URL, SERVICENOW_USER, SERVICENOW_PASSWORD), only when the
 * sync is switched on. The password is never written to a log: ServiceNowConfig hides it.
 */
@Configuration
@ConditionalOnProperty(name = "icc.itsm.mode", havingValue = "servicenow")
public class ServiceNowClientConfiguration {

    @Bean
    public ServiceNowClient serviceNowClient(@Value("${icc.itsm.servicenow.url}") String url,
                                             @Value("${icc.itsm.servicenow.username}") String username,
                                             @Value("${icc.itsm.servicenow.password}") String password) {
        if (url.isBlank() || username.isBlank() || password.isBlank()) {
            throw new IllegalStateException("icc.itsm.mode is servicenow, but SERVICENOW_URL, SERVICENOW_USER or SERVICENOW_PASSWORD is not set");
        }
        return ServiceNowClient.create(new ServiceNowConfig(url, username, password));
    }
}
