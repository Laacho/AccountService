package com.banking.account.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class LoggingAspect {

    private static final String POINTCUT =
            "@within(com.banking.account.annotation.Logged) || @annotation(com.banking.account.aspect.Logged)";

    @Before(POINTCUT)
    public void logBefore(JoinPoint joinPoint) {
        String methodName=joinPoint.getSignature().getName();
        String className=joinPoint.getTarget().getClass().getSimpleName();
        log.info("Starting method: {}.{}",
                methodName,
                className);
    }

    @After(POINTCUT)
    public void logAfter(JoinPoint joinPoint) {
        String methodName=joinPoint.getSignature().getName();
        String className=joinPoint.getTarget().getClass().getSimpleName();
        log.info("Finished method: {}.{}",
               methodName,className);
    }
}