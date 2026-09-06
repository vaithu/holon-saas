package com.holonplatform.multitenant.audit.internal;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.audit.ActorResolver;
import com.holonplatform.multitenant.audit.AuditEntry;
import com.holonplatform.multitenant.audit.AuditSeverity;
import com.holonplatform.multitenant.audit.TenantAuditService;
import com.holonplatform.multitenant.audit.annotation.Audited;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring AOP aspect that intercepts methods annotated with {@link Audited} and
 * forwards the result to {@link TenantAuditService}.
 *
 * <h2>Tenant resolution</h2>
 * <p>The tenant is read from {@link TenantContext#peekTenantId()}. If no tenant
 * is set (background job, admin operation) the entry is logged under the synthetic
 * tenant {@code "system"}.
 *
 * <h2>Actor resolution</h2>
 * <p>Delegated to the injected {@link ActorResolver} (default: {@code "system"}).
 *
 * <h2>SpEL resource ID</h2>
 * <p>If {@link Audited#resourceIdExpression()} is non-blank it is evaluated as a
 * SpEL expression against the method parameters. Parsing is cached per method.
 *
 * <h2>Failure logging</h2>
 * <p>When {@link Audited#logOnFailure()} is {@code true} and the intercepted method
 * throws, an {@link AuditSeverity#WARNING} entry is logged before re-throwing.
 */
@Aspect
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private static final String SYSTEM_TENANT = "system";

    private final TenantAuditService auditService;
    private final ActorResolver      actorResolver;
    private final ExpressionParser   spelParser    = new SpelExpressionParser();
    private final ConcurrentHashMap<String, Expression> exprCache = new ConcurrentHashMap<>();

    public AuditAspect(TenantAuditService auditService, ActorResolver actorResolver) {
        this.auditService  = Objects.requireNonNull(auditService, "auditService must not be null");
        this.actorResolver = Objects.requireNonNull(actorResolver, "actorResolver must not be null");
    }

    @Around("@annotation(audited)")
    public Object around(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        try {
            Object result = pjp.proceed();
            recordEntry(pjp, audited, null);
            return result;
        } catch (Throwable t) {
            if (audited.logOnFailure()) {
                recordFailureEntry(pjp, audited, t);
            }
            throw t;
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void recordEntry(ProceedingJoinPoint pjp, Audited audited, Throwable failure) {
        try {
            String tenantId = resolveTenantId();
            String actor    = actorResolver.resolveActor();
            String desc     = audited.description().isBlank()
                    ? audited.action().getDescription()
                    : audited.description();

            AuditEntry.Builder builder = AuditEntry.builder(tenantId, audited.action())
                    .actor(actor)
                    .severity(audited.severity())
                    .description(desc);

            if (!audited.resourceType().isBlank()) {
                builder.resourceType(audited.resourceType());
            }
            if (!audited.resourceIdExpression().isBlank()) {
                String id = evalResourceId(pjp, audited.resourceIdExpression());
                builder.resourceId(id);
            }

            auditService.log(builder.build());
        } catch (Exception e) {
            log.warn("Failed to record audit entry for @Audited method '{}': {}",
                    pjp.getSignature().toShortString(), e.getMessage());
        }
    }

    private void recordFailureEntry(ProceedingJoinPoint pjp, Audited audited, Throwable failure) {
        try {
            String tenantId = resolveTenantId();
            String actor    = actorResolver.resolveActor();
            String desc     = (audited.description().isBlank()
                    ? audited.action().getDescription()
                    : audited.description())
                    + " [FAILED: " + failure.getMessage() + "]";

            AuditEntry.Builder builder = AuditEntry.builder(tenantId, audited.action())
                    .actor(actor)
                    .severity(AuditSeverity.WARNING)
                    .description(desc);

            if (!audited.resourceType().isBlank()) {
                builder.resourceType(audited.resourceType());
            }

            auditService.log(builder.build());
        } catch (Exception e) {
            log.warn("Failed to record failure audit entry: {}", e.getMessage());
        }
    }

    private static String resolveTenantId() {
        String tid = TenantContext.peekTenantId();
        return tid != null ? tid : SYSTEM_TENANT;
    }

    private String evalResourceId(ProceedingJoinPoint pjp, String spelExpr) {
        try {
            Expression  expr   = exprCache.computeIfAbsent(spelExpr, spelParser::parseExpression);
            MethodSignature sig = (MethodSignature) pjp.getSignature();
            Object[]     args   = pjp.getArgs();

            StandardEvaluationContext ctx = new StandardEvaluationContext(pjp.getThis());

            // Expose args by position: #a0, #a1, ... and #p0, #p1, ...
            for (int i = 0; i < args.length; i++) {
                ctx.setVariable("a" + i, args[i]);
                ctx.setVariable("p" + i, args[i]);
            }

            // Expose args by name when AspectJ provides them (requires -parameters or debug info)
            String[] paramNames = sig.getParameterNames();
            if (paramNames != null) {
                for (int i = 0; i < paramNames.length && i < args.length; i++) {
                    ctx.setVariable(paramNames[i], args[i]);
                }
            }

            Object val = expr.getValue(ctx);
            return val != null ? val.toString() : null;
        } catch (Exception e) {
            log.debug("Could not evaluate resourceIdExpression '{}': {}", spelExpr, e.getMessage());
            return null;
        }
    }
}

