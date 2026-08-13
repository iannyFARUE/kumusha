package com.kumusha.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller handler as a write operation, so that {@link WriteGuardInterceptor} can
 * reject it when the deployment is running read-only.
 *
 * <p>The guard keys off this annotation rather than off the HTTP method so that adding, say, a
 * POST-based search endpoint later does not silently make it unreachable in a read-only
 * deployment. Every endpoint that creates, modifies or removes data - or that spends money
 * calling an external API - should carry it.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface WriteOperation {
}
