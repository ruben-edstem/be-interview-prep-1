package com.mock.taskmanager.security;

import com.mock.taskmanager.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final RequestMatcher LIMITED_REQUESTS = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/auth/login"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/auth/register"));

    private final RateLimiter rateLimiter;
    private final SecurityErrorWriter errorWriter;

    public AuthRateLimitFilter(RateLimitProperties properties, SecurityErrorWriter errorWriter) {
        this.rateLimiter = new RateLimiter(properties.ipRequests(), properties.ipWindow(), Clock.systemUTC());
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !LIMITED_REQUESTS.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String client = request.getRemoteAddr();
        if (!rateLimiter.tryAcquire(client)) {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(rateLimiter.retryAfterSeconds(client)));
            errorWriter.write(response, HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS",
                    "Too many requests, try again later");
            return;
        }
        chain.doFilter(request, response);
    }
}
