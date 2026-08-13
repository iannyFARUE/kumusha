package com.kumusha.config;

import com.kumusha.exception.WriteOperationsDisabledException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Rejects {@link WriteOperation} handlers unless writes have been explicitly enabled.
 *
 * <p>The API exposes unauthenticated batch update and delete endpoints, which are useful when
 * demonstrating MongoDB write operations locally but are not something to leave reachable on a
 * public deployment. Rather than adding credentials, this guard makes read-only the default and
 * requires a deliberate opt-in to turn writing on.
 *
 * <p>The property defaults to false, so a deployment that simply forgets to configure it ends up
 * read-only rather than wide open. The documented local setup in {@code server/.env.example} sets
 * {@code WRITE_ENABLED=true}, so following the README still gives a fully writable dev server.
 *
 * <p>The rejection is raised as an exception rather than written directly to the response so that
 * it flows through GlobalExceptionHandler and produces the same error envelope as every other
 * failure the API returns.
 */
@Component
public class WriteGuardInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(WriteGuardInterceptor.class);

    private final boolean writeEnabled;

    public WriteGuardInterceptor(@Value("${kumusha.write.enabled:false}") boolean writeEnabled) {
        this.writeEnabled = writeEnabled;

        if (writeEnabled) {
            logger.warn("Write operations are ENABLED. Do not use this configuration for a "
                    + "publicly reachable deployment: the create, update, delete and embedding "
                    + "backfill endpoints are unauthenticated.");
        } else {
            logger.info("Write operations are disabled (read-only mode). Set WRITE_ENABLED=true "
                    + "to allow create, update, delete and embedding backfill requests.");
        }
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {

        if (writeEnabled || !(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        if (!handlerMethod.hasMethodAnnotation(WriteOperation.class)) {
            return true;
        }

        logger.warn("Rejected {} {}: write operations are disabled",
                request.getMethod(), request.getRequestURI());

        throw new WriteOperationsDisabledException(
                "This deployment is read-only. Create, update, delete and embedding backfill "
                        + "operations are disabled. Run the server locally with WRITE_ENABLED=true "
                        + "to use them.");
    }
}
