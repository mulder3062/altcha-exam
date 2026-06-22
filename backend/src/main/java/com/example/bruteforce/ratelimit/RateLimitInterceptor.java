package com.example.bruteforce.ratelimit;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 비즈니스 로직 진입 전 IP 차단 여부를 검사한다.
 * 차단 중이면 HTTP 429 + Retry-After(초) 헤더로 즉시 응답한다.
 * (checkup-backend-rate-limit.md §2·§6)
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String BLOCKED_BODY =
            "{\"message\":\"요청이 많아 잠시 후 다시 시도해 주세요.\"}";

    private final RateLimitService rateLimitService;
    private final ClientIpResolver ipResolver;

    public RateLimitInterceptor(RateLimitService rateLimitService, ClientIpResolver ipResolver) {
        this.rateLimitService = rateLimitService;
        this.ipResolver = ipResolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String ip = ipResolver.resolve(request);

        if (rateLimitService.isBlocked(ip)) {
            long retryAfter = rateLimitService.retryAfterSeconds(ip);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());      // 429
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter)); // 남은 차단 초
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(BLOCKED_BODY); // 응답 본문은 일반화 메시지만
            return false; // 비즈니스 로직 진입 차단
        }
        return true;
    }
}
