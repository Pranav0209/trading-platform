package com.zkuba.trading.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * AOP Aspect for centralized logging across the application.
 * Logs method entry, exit, execution time, and exceptions.
 */
@Aspect
@Component
public class LoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);

    /**
     * Log all method calls in service layer
     */
    @Pointcut("execution(* com.zkuba.trading.service..*(..))")
    public void serviceLayer() {}

    /**
     * Log all method calls in controller layer
     */
    @Pointcut("execution(* com.zkuba.trading.controller..*(..))")
    public void controllerLayer() {}

    /**
     * Log all method calls in repository layer
     */
    @Pointcut("execution(* com.zkuba.trading.repository..*(..))")
    public void repositoryLayer() {}

    /**
     * Around advice to log method execution time and parameters
     */
    @Around("serviceLayer() || repositoryLayer()")
    public Object aroundServiceAndRepository(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.nanoTime();
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        Object[] args = joinPoint.getArgs();

        logger.debug("[{}:{}] Method started with args: {}", 
            className, methodName, formatArgs(args));

        try {
            Object result = joinPoint.proceed();
            long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            logger.debug("[{}:{}] Method completed successfully in {}ms", 
                className, methodName, duration);
            return result;
        } catch (Throwable throwable) {
            long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            logger.error("[{}:{}] Method failed after {}ms with exception: {}", 
                className, methodName, duration, throwable.getClass().getSimpleName(), throwable);
            throw throwable;
        }
    }

    /**
     * Around advice to log controller method execution
     */
    @Around("controllerLayer()")
    public Object aroundController(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.nanoTime();
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        Object[] args = joinPoint.getArgs();

        logger.info("[API] {}::{} called", className, methodName);
        logger.debug("[API] {}::{} parameters: {}", className, methodName, formatArgs(args));

        try {
            Object result = joinPoint.proceed();
            long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            logger.info("[API] {}::{} completed in {}ms", className, methodName, duration);
            return result;
        } catch (Throwable throwable) {
            long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            logger.error("[API] {}::{} failed after {}ms - {}", 
                className, methodName, duration, throwable.getMessage(), throwable);
            throw throwable;
        }
    }

    /**
     * Before advice to log method entry
     */
    @Before("controllerLayer()")
    public void beforeController(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        logger.debug("[ENTER] {}.{}", className, methodName);
    }

    /**
     * After returning advice to log successful method exit
     */
    @AfterReturning(pointcut = "controllerLayer()", returning = "result")
    public void afterReturningController(JoinPoint joinPoint, Object result) {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        logger.debug("[EXIT] {}.{} returned: {}", 
            className, methodName, 
            result != null ? result.getClass().getSimpleName() : "null");
    }

    /**
     * After throwing advice to log exceptions
     */
    @AfterThrowing(pointcut = "serviceLayer() || repositoryLayer() || controllerLayer()", throwing = "exception")
    public void afterThrowingException(JoinPoint joinPoint, Throwable exception) {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        logger.error("[EXCEPTION] {}.{} threw {}: {}", 
            className, methodName, 
            exception.getClass().getSimpleName(), 
            exception.getMessage());
    }

    /**
     * Format arguments for logging (avoid logging sensitive data)
     */
    private String formatArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            Object arg = args[i];
            if (arg == null) {
                sb.append("null");
            } else if (arg instanceof String) {
                // Avoid logging passwords or sensitive strings
                String str = (String) arg;
                if (str.toLowerCase().contains("password") || str.toLowerCase().contains("secret")) {
                    sb.append("***");
                } else {
                    sb.append("'").append(str).append("'");
                }
            } else if (arg instanceof Number || arg instanceof Boolean) {
                sb.append(arg);
            } else {
                sb.append(arg.getClass().getSimpleName());
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
