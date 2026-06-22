package com.example.bruteforce.auth;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.bruteforce.altcha.AltchaVerifier;
import com.example.bruteforce.ratelimit.ClientIpResolver;
import com.example.bruteforce.ratelimit.RateLimitService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 로그인 엔드포인트.
 * 방어 계층 순서:
 *   (0) IP Rate Limit  — RateLimitInterceptor 가 진입 전 선차단(429)
 *   (1) Altcha PoW 검증 + 재사용 방지
 *   (2) 자격증명 일치 확인 (enumeration 방어: 동일 일반 메시지)
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final AltchaVerifier altchaVerifier;
    private final RateLimitService rateLimitService;
    private final ClientIpResolver ipResolver;

    public AuthController(AuthService authService,
                         AltchaVerifier altchaVerifier,
                         RateLimitService rateLimitService,
                         ClientIpResolver ipResolver) {
        this.authService = authService;
        this.altchaVerifier = altchaVerifier;
        this.rateLimitService = rateLimitService;
        this.ipResolver = ipResolver;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req, HttpServletRequest request)
            throws Exception {
        String ip = ipResolver.resolve(request);

        // (1) Altcha PoW 검증 + 재사용 방지
        if (!altchaVerifier.verify(req.altcha())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "캡차 검증에 실패했습니다. 다시 시도해 주세요."));
        }

        // (2) 자격증명 일치 확인
        boolean matched = authService.matches(req.username(), req.password());
        if (!matched) {
            // 실패 카운트 누적(임계 초과 시 다음 요청부터 인터셉터가 429 차단)
            rateLimitService.recordFailure(ip);
            // enumeration 방어: 어떤 필드가 틀렸는지 노출하지 않는 동일 메시지
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "아이디 또는 비밀번호가 올바르지 않습니다."));
        }

        // 성공 → 카운터 리셋, 데모용 토큰 발급
        rateLimitService.reset(ip);
        return ResponseEntity.ok(Map.of(
                "message", "로그인 성공",
                "token", "demo-session-token" // 실서비스라면 세션/JWT 발급
        ));
    }
}
