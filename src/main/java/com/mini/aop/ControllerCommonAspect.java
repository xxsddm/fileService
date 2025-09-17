package com.mini.aop;

import com.mini.nio.NioAsyncExecutor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * AOP aspect for logging controller method invocations
 */
@Aspect
@Component
@Slf4j
public class ControllerCommonAspect {

    // Pointcut for all controller methods
    @Pointcut("@annotation(com.mini.annotation.ControllerCommonAnnotation)")
    public void controllerPointcut() {
    }

    // Around advice to log before and after method execution
    @Around("controllerPointcut()")
    public Object logAroundControllerMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        // Get current request
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes.getRequest();

        // Get method information
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String className = joinPoint.getTarget().getClass().getName();
        String methodName = method.getName();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));

        // Log method start
        log.info("[API] Request started at {} | Method: {} | URL: {} | Class: {} | Method: {}",
                timestamp,
                request.getMethod(),
                request.getRequestURL().toString(),
                className,
                methodName);

        Instant startTime = Instant.now();
        Object result;

        try {
            // Execute the method asynchronously
            result = joinPoint.proceed();
            Instant endTime = Instant.now();
            long executionTime = Duration.between(startTime, endTime).toMillis();

            // Log method end
            log.info("[API] Request completed at {} | Class: {} | Method: {} | Execution time: {} ms | Status: SUCCESS",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")),
                    className,
                    methodName,
                    executionTime);

            return result;
        } catch (Exception e) {
            Instant endTime = Instant.now();
            long executionTime = Duration.between(startTime, endTime).toMillis();

            // Log method failure
            log.error("[API] Request failed at {} | Class: {} | Method: {} | Execution time: {} ms | Status: FAILED | Error: {}",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")),
                    className,
                    methodName,
                    executionTime,
                    e.getMessage());

            throw e; // Rethrow the exception
        }
    }
}