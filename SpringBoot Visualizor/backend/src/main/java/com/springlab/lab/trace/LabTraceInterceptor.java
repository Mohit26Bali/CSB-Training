package com.springlab.lab.trace;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.List;

/**
 * Runs when DispatcherServlet has chosen a controller method (after the whole security filter chain). It records which
 * handler was chosen and who the security context says the caller is, then switches the trace into the HANDLER phase.
 */
public class LabTraceInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        TraceCollector collector = TraceContext.current();
        if (collector != null && handler instanceof HandlerMethod handlerMethod) {
            String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            RawTrace.HandlerInfo info = new RawTrace.HandlerInfo(handlerMethod.getBeanType().getSimpleName(),
                    handlerMethod.getMethod().getName(), pattern);
            collector.handlerEntered(info, authentication());
        }
        return true;
    }

    private static RawTrace.AuthInfo authentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        List<String> authorities = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList();
        return new RawTrace.AuthInfo(auth.getName(), authorities);
    }
}
