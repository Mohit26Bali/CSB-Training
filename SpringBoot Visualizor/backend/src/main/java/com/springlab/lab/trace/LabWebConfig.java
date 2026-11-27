package com.springlab.lab.trace;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class LabWebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Does nothing unless the request is being traced (see TraceContext).
        registry.addInterceptor(new LabTraceInterceptor()).addPathPatterns("/api/**");
    }
}
