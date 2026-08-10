package com.kumusha.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * HTTP Request Logging Filter
 *
 * <p>This filter logs all incoming HTTP requests with useful information
 * including method, URL, status code, and response time.
 *
 * <p>Log output format:
 * <pre>
 * INFO  - GET /api/listings 200 - 45ms
 * WARN  - GET /api/listings/unknown 404 - 2ms
 * ERROR - POST /api/listings 500 - 120ms
 * </pre>
 *
 * <p>The filter is ordered to run first in the filter chain to ensure
 * accurate timing measurements.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        long startTime = System.currentTimeMillis();

        logger.debug("Incoming request: {} {} from {}",
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr());

        try {
            filterChain.doFilter(request, response);
        } finally {
            long responseTime = System.currentTimeMillis() - startTime;
            logRequest(request.getMethod(), request.getRequestURI(), response.getStatus(), responseTime);
        }
    }

    /**
     * Logs the HTTP request with appropriate log level based on status code.
     *
     * <ul>
     *   <li>ERROR: 5xx server errors</li>
     *   <li>WARN: 4xx client errors</li>
     *   <li>INFO: 2xx and 3xx success/redirect</li>
     * </ul>
     */
    private void logRequest(String method, String uri, int statusCode, long responseTime) {
        String message = String.format("%s %s %d - %dms", method, uri, statusCode, responseTime);

        if (statusCode >= 500) {
            logger.error(message);
        } else if (statusCode >= 400) {
            logger.warn(message);
        } else {
            logger.info(message);
        }
    }

    /**
     * Skip logging for static resources and API docs to reduce noise.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/swagger-ui")
                || path.startsWith("/api-docs")
                || path.startsWith("/v3/api-docs")
                || path.equals("/favicon.ico")
                || path.startsWith("/actuator");
    }
}
