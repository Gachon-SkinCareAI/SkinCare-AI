package skincare.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Metadata-only request timings; never log identifiers, bodies, cookies or headers. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
// AnalysisTimingFilter 인증과 권한 처리
public class AnalysisTimingFilter extends OncePerRequestFilter {
    private static final org.slf4j.Logger PERFORMANCE =
            org.slf4j.LoggerFactory.getLogger("skincare.performance");

    static String operation(String method, String path) {
        if (!"POST".equals(method)) return null;
        if ("/api/wc/analyses".equals(path)) return "create";
        if (path.matches("/api/wc/analyses/[a-zA-Z0-9-]+/answers")) return "answer";
        if (path.matches("/api/wc/analyses/[a-zA-Z0-9-]+/retry")) return "retry";
        return null;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String operation = operation(request.getMethod(), request.getRequestURI());
        if (operation == null) {
            chain.doFilter(request, response);
            return;
        }
        long started = System.nanoTime();
        boolean completed = false;
        try {
            chain.doFilter(request, response);
            completed = true;
        } finally {
            PERFORMANCE.info(
                    "event=analysis_request operation={} status={} completed={} elapsed_ms={}",
                    operation,
                    response.getStatus(),
                    completed,
                    (System.nanoTime() - started) / 1_000_000);
        }
    }
}
