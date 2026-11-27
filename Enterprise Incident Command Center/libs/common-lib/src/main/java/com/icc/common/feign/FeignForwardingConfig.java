package com.icc.common.feign;

import com.icc.common.correlation.CorrelationIds;
import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Makes every Feign call carry the caller's token and the correlation id (D7, ARCHITECTURE).
 * Only active in a service that has Feign on its classpath. A header that the caller set
 * explicitly on the Feign method is never overwritten (the Dashboard does that, because its
 * calls run on other threads where the request is not available).
 */
@Configuration
@ConditionalOnClass(name = "feign.RequestInterceptor")
@Import(FeignExceptionHandler.class)
public class FeignForwardingConfig {

    @Bean
    public RequestInterceptor forwardCallerContext() {
        return template -> {
            if (!template.headers().containsKey(HttpHeaders.AUTHORIZATION)) {
                RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
                if (attributes instanceof ServletRequestAttributes servletAttributes) {
                    String authorization = servletAttributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
                    if (authorization != null) {
                        template.header(HttpHeaders.AUTHORIZATION, authorization);
                    }
                }
            }
            if (!template.headers().containsKey(CorrelationIds.HEADER)) {
                String correlationId = MDC.get(CorrelationIds.MDC_KEY);
                if (correlationId != null) {
                    template.header(CorrelationIds.HEADER, correlationId);
                }
            }
        };
    }
}
