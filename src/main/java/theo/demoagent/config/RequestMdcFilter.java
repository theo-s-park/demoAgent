package theo.demoagent.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Adds request-scoped MDC fields:
 * - rid: request id (returned as X-Request-Id)
 * - path: request path
 * - method: HTTP method
 */
@Component
public class RequestMdcFilter extends OncePerRequestFilter {

    private static final String HDR = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String rid = Optional.ofNullable(request.getHeader(HDR))
                .filter(s -> !s.isBlank())
                .orElse(UUID.randomUUID().toString().substring(0, 8));
        MDC.put("rid", rid);
        MDC.put("path", request.getRequestURI());
        MDC.put("method", request.getMethod());
        response.setHeader(HDR, rid);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}

