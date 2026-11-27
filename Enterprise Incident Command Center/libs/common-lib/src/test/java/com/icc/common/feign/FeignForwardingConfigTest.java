package com.icc.common.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FeignForwardingConfigTest {

    private final RequestInterceptor interceptor = new FeignForwardingConfig().forwardCallerContext();

    @AfterEach
    void clean() {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    private void callerSends(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", authorization);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void forwardsTheCallersTokenAndCorrelationId() {
        callerSends("Bearer abc");
        MDC.put("correlationId", "cid-1");
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertEquals(List.of("Bearer abc"), List.copyOf(template.headers().get("Authorization")));
        assertEquals(List.of("cid-1"), List.copyOf(template.headers().get("X-Correlation-Id")));
    }

    @Test
    void anExplicitHeaderIsNotOverwritten() {
        callerSends("Bearer from-request");
        RequestTemplate template = new RequestTemplate();
        template.header("Authorization", "Bearer explicit");

        interceptor.apply(template);

        assertEquals(List.of("Bearer explicit"), List.copyOf(template.headers().get("Authorization")));
    }

    @Test
    void withoutARequestNothingIsAdded() {
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertFalse(template.headers().containsKey("Authorization"));
        assertFalse(template.headers().containsKey("X-Correlation-Id"));
    }
}
