package demo.aop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TimingAspect {
    private static final Logger log = LoggerFactory.getLogger(TimingAspect.class);
    /** Names of the methods that were timed (so a test can check it). */
    public static final List<String> TIMED = Collections.synchronizedList(new ArrayList<>());

    @Around("@annotation(demo.aop.Timed)")
    public Object time(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.nanoTime();
        try {
            return pjp.proceed();                       // run the real method
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            TIMED.add(pjp.getSignature().toShortString());
            log.info("{} took {} ms", pjp.getSignature().toShortString(), ms);
        }
    }
}
