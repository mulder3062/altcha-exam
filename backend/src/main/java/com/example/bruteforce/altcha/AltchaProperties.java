package com.example.bruteforce.altcha;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * altcha.* 설정 매핑. (checkup-b-type-altcha-pilot.md §5.2)
 */
@ConfigurationProperties(prefix = "altcha")
public class AltchaProperties {

    /** HMAC 서명 비밀키(환경변수 ALTCHA_HMAC_KEY 주입 권장) */
    private String hmacKey;

    /** PoW 난이도. 클수록 풀이 비용 증가 */
    private long maxNumber = 50_000;

    /** 챌린지 TTL(초) */
    private int expiresSeconds = 300;

    public String getHmacKey() {
        return hmacKey;
    }

    public void setHmacKey(String hmacKey) {
        this.hmacKey = hmacKey;
    }

    public long getMaxNumber() {
        return maxNumber;
    }

    public void setMaxNumber(long maxNumber) {
        this.maxNumber = maxNumber;
    }

    public int getExpiresSeconds() {
        return expiresSeconds;
    }

    public void setExpiresSeconds(int expiresSeconds) {
        this.expiresSeconds = expiresSeconds;
    }
}
