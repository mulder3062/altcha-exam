package com.example.bruteforce.altcha;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.altcha.altcha.v1.Altcha;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Altcha PoW 솔루션 검증 + 재사용(replay) 방지.
 * Altcha 는 솔루션 검증만 할 뿐 1회용을 보장하지 않으므로,
 * 사용된 payload 를 인메모리에 기록해 재사용을 막는다.
 * (checkup-b-type-altcha-pilot.md §5.4 / §7)
 */
@Component
public class AltchaVerifier {

    private final AltchaProperties props;

    // 사용된 솔루션 기록 (payload 해시 → 만료시각). @Scheduled 로 주기 정리.
    private final Map<String, Instant> usedSolutions = new ConcurrentHashMap<>();

    public AltchaVerifier(AltchaProperties props) {
        this.props = props;
    }

    /**
     * @return PoW·서명·만료 검증을 통과하고, 아직 사용된 적 없는 payload 면 true
     */
    public boolean verify(String altchaPayloadBase64) throws Exception {
        if (altchaPayloadBase64 == null || altchaPayloadBase64.isBlank()) {
            return false;
        }

        // 1) PoW + HMAC 서명 + 만료(checkExpires=true) 로컬 검증
        boolean ok = Altcha.verifySolution(altchaPayloadBase64, props.getHmacKey(), true);
        if (!ok) {
            return false;
        }

        // 2) 재사용 방지 (1회용)
        String fingerprint = sha256(altchaPayloadBase64);
        Instant prev = usedSolutions.putIfAbsent(
                fingerprint, Instant.now().plusSeconds(props.getExpiresSeconds()));
        return prev == null; // 이미 쓰인 payload 면 거부
    }

    @Scheduled(fixedRate = 60_000)
    public void evictExpired() {
        Instant now = Instant.now();
        usedSolutions.entrySet().removeIf(e -> e.getValue().isBefore(now));
    }

    private String sha256(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
