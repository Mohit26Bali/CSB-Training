package com.icc.common.web;

import com.icc.common.correlation.CorrelationIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void missingHeader_createsAnId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String id = response.getHeader(CorrelationIds.HEADER);
        assertNotNull(id);
        assertEquals(36, id.length());
    }

    @Test
    void existingHeader_isKeptAndReturned() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIds.HEADER, "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("abc-123", response.getHeader(CorrelationIds.HEADER));
    }

    @Test
    void tooLongHeader_isReplaced() throws Exception {
        String tooLong = "x".repeat(100);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIds.HEADER, tooLong);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String id = response.getHeader(CorrelationIds.HEADER);
        assertNotEquals(tooLong, id);
        assertEquals(36, id.length());
    }

    @Test
    void idIsInMdcDuringTheRequest_andRemovedAfter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIds.HEADER, "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seenInsideChain = new String[1];
        FilterChain chain = (req, res) -> seenInsideChain[0] = MDC.get(CorrelationIds.MDC_KEY);

        filter.doFilter(request, response, chain);

        assertEquals("abc-123", seenInsideChain[0]);
        assertNull(MDC.get(CorrelationIds.MDC_KEY));
    }

    @Test
    void idIsRemovedEvenWhenTheRequestFails() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain failingChain = (req, res) -> {
            throw new ServletException("boom");
        };

        assertThrows(ServletException.class, () -> filter.doFilter(request, response, failingChain));

        assertNull(MDC.get(CorrelationIds.MDC_KEY));
    }

    @Test
    void createdId_isVisibleAsRequestHeaderDownstream() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] headerSeenDownstream = new String[1];
        boolean[] listedInNames = new boolean[1];
        FilterChain chain = (req, res) -> {
            HttpServletRequest downstream = (HttpServletRequest) req;
            headerSeenDownstream[0] = downstream.getHeader(CorrelationIds.HEADER);
            listedInNames[0] = java.util.Collections.list(downstream.getHeaderNames())
                    .stream().anyMatch(CorrelationIds.HEADER::equalsIgnoreCase);
        };

        filter.doFilter(request, response, chain);

        assertNotNull(headerSeenDownstream[0]);
        assertEquals(response.getHeader(CorrelationIds.HEADER), headerSeenDownstream[0]);
        assertEquals(true, listedInNames[0]);
    }

    @Test
    void replacedId_isTheOneSeenDownstream() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIds.HEADER, "x".repeat(100));
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] headerSeenDownstream = new String[1];
        FilterChain chain = (req, res) ->
                headerSeenDownstream[0] = ((HttpServletRequest) req).getHeader(CorrelationIds.HEADER);

        filter.doFilter(request, response, chain);

        assertEquals(36, headerSeenDownstream[0].length());
        assertEquals(response.getHeader(CorrelationIds.HEADER), headerSeenDownstream[0]);
    }

    @Test
    void existingId_isSeenDownstreamExactlyOnce() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("x-correlation-id", "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        long[] timesListed = new long[1];
        FilterChain chain = (req, res) -> timesListed[0] = java.util.Collections
                .list(((HttpServletRequest) req).getHeaderNames())
                .stream().filter(CorrelationIds.HEADER::equalsIgnoreCase).count();

        filter.doFilter(request, response, chain);

        assertEquals(1, timesListed[0]);
    }
}
