package com.springlab.lab.trace;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

/**
 * Observes calls to SpringLab services and Spring Data repositories, but ONLY while the current thread is running a
 * traced request ({@link TraceContext#current()} non-null). For every other request it simply proceeds.
 * It records the type, method name, duration and exception class; never arguments, return values or messages.
 */
@Aspect
@Component
public class LabTraceAspect {

    private static final String SERVICE_PACKAGE = "com.springlab.service";
    private static final String REPOSITORY_PACKAGE = "com.springlab.repository";

    @Around("within(com.springlab.service..*) || bean(*Repository)")
    public Object observe(ProceedingJoinPoint joinPoint) throws Throwable {
        TraceCollector collector = TraceContext.current();
        if (collector == null) {
            return joinPoint.proceed();
        }
        Class<?> targetClass = AopUtils.getTargetClass(joinPoint.getTarget());
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        boolean service = targetClass.getName().startsWith(SERVICE_PACKAGE);

        TraceCollector.OpenCall call = collector.enter(
                service ? Layer.SERVICE : Layer.REPOSITORY,
                service ? targetClass.getSimpleName() : repositoryName(joinPoint.getThis()),
                method.getName(),
                service ? transactionalDescription(method, targetClass) : null);
        Throwable thrown = null;
        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            thrown = t;
            throw t;
        } finally {
            collector.exit(call, thrown);
        }
    }

    /** The Spring Data repository proxy implements the SpringLab interface (e.g. UserRepository). */
    private static String repositoryName(Object proxy) {
        for (Class<?> type : proxy.getClass().getInterfaces()) {
            if (type.getName().startsWith(REPOSITORY_PACKAGE)) {
                return type.getSimpleName();
            }
        }
        return AopUtils.getTargetClass(proxy).getSimpleName();
    }

    /** Reads the annotation from source (method first, then class); this is a declaration, not live transaction state. */
    private static String transactionalDescription(Method method, Class<?> targetClass) {
        Method specific = AopUtils.getMostSpecificMethod(method, targetClass);
        Transactional tx = AnnotatedElementUtils.findMergedAnnotation(specific, Transactional.class);
        if (tx == null) {
            tx = AnnotatedElementUtils.findMergedAnnotation(targetClass, Transactional.class);
        }
        if (tx == null) {
            return null;
        }
        return tx.readOnly() ? "declared, readOnly = true" : "declared";
    }
}
