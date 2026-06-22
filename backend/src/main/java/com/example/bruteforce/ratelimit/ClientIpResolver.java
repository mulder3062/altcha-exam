package com.example.bruteforce.ratelimit;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 클라이언트 IP 추출. 프록시 환경을 고려해 X-Forwarded-For 를 우선 사용한다.
 * (운영에서는 신뢰 가능한 프록시 뒤에서만 X-Forwarded-For 를 신뢰할 것)
 */
@Component
public class ClientIpResolver {

    public String resolve(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // "client, proxy1, proxy2" 형태 → 첫 번째가 원 클라이언트
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
