package com.icc.common.web;

import com.icc.common.correlation.CorrelationIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String id = request.getHeader(CorrelationIds.HEADER);
        if (id == null || id.isBlank() || id.length() > 64) {
            id = UUID.randomUUID().toString();
        }

        MDC.put(CorrelationIds.MDC_KEY, id);
        response.setHeader(CorrelationIds.HEADER, id);
        try {
            chain.doFilter(new RequestWithCorrelationId(request, id), response);
        } finally {
            MDC.remove(CorrelationIds.MDC_KEY);
        }
    }

    // A request cannot be changed, so this wrapper answers header questions with our id.
    private static class RequestWithCorrelationId extends HttpServletRequestWrapper {

        private final String id;

        RequestWithCorrelationId(HttpServletRequest request, String id) {
            super(request);
            this.id = id;
        }

        @Override
        public String getHeader(String name) {
            return CorrelationIds.HEADER.equalsIgnoreCase(name) ? id : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return CorrelationIds.HEADER.equalsIgnoreCase(name)
                    ? Collections.enumeration(List.of(id))
                    : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            names.addAll(Collections.list(super.getHeaderNames()));
            names.add(CorrelationIds.HEADER);
            return Collections.enumeration(names);
        }
    }
}
