package com.chatapp.common.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Pushes the fully-qualified caller method name into MDC key "sqlCaller" before each service /
 * repository invocation. SqlTracingStatementInspector reads this key and appends it to every SQL
 * statement that Hibernate executes, producing log lines like:
 *
 * <p>[sqlCaller=MessageService.findPagedMessages] SELECT m.* FROM messages m WHERE ...
 *
 * <p>Activated only when app.sql-trace.enabled=true (default: false) to avoid overhead in
 * production.
 */
@Aspect
@Component
@ConditionalOnProperty(name = "app.sql-trace.enabled", havingValue = "true")
public class SqlTraceAspect {

  private static final String MDC_KEY = "sqlCaller";

  // Intercepts all public methods in service and repository packages
  @Around(
      "execution(public * com.chatapp..service..*(..))"
          + " || execution(public * com.chatapp..repository..*(..))")
  public Object trace(ProceedingJoinPoint pjp) throws Throwable {
    MethodSignature sig = (MethodSignature) pjp.getSignature();
    String caller = sig.getDeclaringType().getSimpleName() + "." + sig.getName();

    String previous = MDC.get(MDC_KEY);
    // Preserve the outermost caller (service layer) rather than overwriting with repo
    if (previous == null) {
      MDC.put(MDC_KEY, caller);
    }
    try {
      return pjp.proceed();
    } finally {
      if (previous == null) {
        MDC.remove(MDC_KEY);
      }
    }
  }
}
