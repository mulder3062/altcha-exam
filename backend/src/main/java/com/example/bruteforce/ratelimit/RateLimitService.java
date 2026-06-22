package com.example.bruteforce.ratelimit;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * IP 단위 점진적 차단 서비스 (인메모리).
 * checkup-backend-rate-limit.md §3·§4 패턴:
 *   - ConcurrentHashMap 으로 IP별 카운터·차단 상태 관리
 *   - 반복 위반 시 차단 시간 단계적 증가(1→2→3+, 상한)
 *   - @Scheduled 로 만료 키 정리(메모리 누수 방지)
 *
 * 브루트포스 로그인 데모이므로 "요청량"이 아닌 "로그인 실패 횟수" 기준으로 카운트한다.
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private final RateLimitProperties props;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    public RateLimitService(RateLimitProperties props) {
        this.props = props;
    }

    /** IP별 카운터/차단 상태 */
    private static class Counter {
        int failures;          // 현재 윈도우 내 실패 횟수
        Instant windowStart;   // 윈도우 시작시각
        int blockStage;        // 현재까지의 차단 단계(0=미차단)
        Instant blockedUntil;  // 차단 해제시각(null=차단 아님)
    }

    /** 현재 차단 중인지 */
    public boolean isBlocked(String ip) {
        Counter c = counters.get(ip);
        return c != null && c.blockedUntil != null && c.blockedUntil.isAfter(Instant.now());
    }

    /** 남은 차단 시간(초). 차단 중이 아니면 0 */
    public long retryAfterSeconds(String ip) {
        Counter c = counters.get(ip);
        if (c == null || c.blockedUntil == null) {
            return 0;
        }
        long remaining = c.blockedUntil.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(remaining, 0);
    }

    /**
     * 로그인 실패 기록. 윈도우 내 실패가 임계치를 초과하면 차단 단계를 올리고 차단시킨다.
     */
    public synchronized void recordFailure(String ip) {
        Instant now = Instant.now();
        Counter c = counters.computeIfAbsent(ip, k -> {
            Counter nc = new Counter();
            nc.windowStart = now;
            return nc;
        });

        // 윈도우 만료 시 카운터 리셋(차단 단계는 유지 → 반복 위반 시 점진적 상승)
        if (now.isAfter(c.windowStart.plusSeconds(props.getWindowSeconds()))) {
            c.failures = 0;
            c.windowStart = now;
        }

        c.failures++;

        if (c.failures >= props.getMaxFailures()) {
            c.blockStage++;
            long blockSeconds = props.blockSecondsForStage(c.blockStage);
            c.blockedUntil = now.plusSeconds(blockSeconds);
            c.failures = 0;          // 차단 후 카운터 리셋
            c.windowStart = now;
            log.warn("Rate limit 차단: ip={} stage={} blockSeconds={}", ip, c.blockStage, blockSeconds);
        }
    }

    /** 로그인 성공 시 카운터 제거(정상 사용자 영향 최소화) */
    public void reset(String ip) {
        counters.remove(ip);
    }

    /** 만료된 키 주기 정리 */
    @Scheduled(fixedRateString = "#{${security.rate-limit.cleanup-interval-seconds:30} * 1000}")
    public void evictExpired() {
        Instant now = Instant.now();
        counters.entrySet().removeIf(e -> {
            Counter c = e.getValue();
            boolean notBlocked = c.blockedUntil == null || c.blockedUntil.isBefore(now);
            boolean windowExpired = now.isAfter(c.windowStart.plusSeconds(props.getWindowSeconds()));
            // 차단도 풀렸고 윈도우도 만료된 키만 제거
            return notBlocked && windowExpired;
        });
    }
}
