package demo.aop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(1)                                                // lower number = runs first (outermost)
public class AuditAspect {
    public static final List<String> EVENTS = Collections.synchronizedList(new ArrayList<>());

    @Pointcut("execution(public * demo.service.OrderService.*(..))")
    void orderServiceMethods() { }                       // a named, reusable pointcut

    @Before("orderServiceMethods()")
    public void before(JoinPoint jp) {
        EVENTS.add("before " + jp.getSignature().getName());
    }

    @AfterReturning(pointcut = "orderServiceMethods()", returning = "result")
    public void afterReturning(JoinPoint jp, Object result) {
        EVENTS.add("returned " + result);
    }

    @AfterThrowing(pointcut = "orderServiceMethods()", throwing = "ex")
    public void afterThrowing(JoinPoint jp, Exception ex) {
        EVENTS.add("failed " + ex.getMessage());
    }
}
