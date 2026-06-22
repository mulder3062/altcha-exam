package com.example.bruteforce.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * security.rate-limit.* 설정 매핑.
 * (checkup-backend-rate-limit.md §5 기준, 데모는 초단위 값 사용)
 */
@ConfigurationProperties(prefix = "security.rate-limit")
public class RateLimitProperties {

    /** 실패 카운팅 윈도우(초) */
    private long windowSeconds = 60;

    /** 윈도우 내 허용 실패 횟수(초과 시 차단) */
    private int maxFailures = 5;

    /** 1단계 차단(초) */
    private long blockStage1Seconds = 15;

    /** 2단계 차단(초) */
    private long blockStage2Seconds = 30;

    /** 3단계 이상 차단 상한(초) */
    private long blockStageMaxSeconds = 60;

    /** 만료 키 정리 주기(초) */
    private long cleanupIntervalSeconds = 30;

    public long getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(long windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public int getMaxFailures() {
        return maxFailures;
    }

    public void setMaxFailures(int maxFailures) {
        this.maxFailures = maxFailures;
    }

    public long getBlockStage1Seconds() {
        return blockStage1Seconds;
    }

    public void setBlockStage1Seconds(long blockStage1Seconds) {
        this.blockStage1Seconds = blockStage1Seconds;
    }

    public long getBlockStage2Seconds() {
        return blockStage2Seconds;
    }

    public void setBlockStage2Seconds(long blockStage2Seconds) {
        this.blockStage2Seconds = blockStage2Seconds;
    }

    public long getBlockStageMaxSeconds() {
        return blockStageMaxSeconds;
    }

    public void setBlockStageMaxSeconds(long blockStageMaxSeconds) {
        this.blockStageMaxSeconds = blockStageMaxSeconds;
    }

    public long getCleanupIntervalSeconds() {
        return cleanupIntervalSeconds;
    }

    public void setCleanupIntervalSeconds(long cleanupIntervalSeconds) {
        this.cleanupIntervalSeconds = cleanupIntervalSeconds;
    }

    /** 차단 단계(1,2,3+)에 해당하는 차단 시간(초) 반환 */
    public long blockSecondsForStage(int stage) {
        return switch (stage) {
            case 1 -> blockStage1Seconds;
            case 2 -> blockStage2Seconds;
            default -> blockStageMaxSeconds; // 3단계 이상은 상한
        };
    }
}
