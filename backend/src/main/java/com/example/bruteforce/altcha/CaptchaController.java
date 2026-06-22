package com.example.bruteforce.altcha;

import org.altcha.altcha.v1.Altcha;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Altcha 위젯이 challengeurl 로 호출하는 챌린지 발급 엔드포인트.
 * createChallenge 는 로컬 HMAC/해시 연산만 수행(외부 통신 없음).
 * (checkup-b-type-altcha-pilot.md §5.3, v1 = SHA-256 hashcash = 위젯 기본 호환)
 */
@RestController
@RequestMapping("/captcha")
public class CaptchaController {

    private static final Logger log = LoggerFactory.getLogger(CaptchaController.class);

    private final AltchaProperties props;

    public CaptchaController(AltchaProperties props) {
        this.props = props;
        if (props.getHmacKey() == null || props.getHmacKey().contains("demo-insecure")) {
            log.warn("ALTCHA_HMAC_KEY 가 데모 기본값입니다. 운영에서는 반드시 환경변수로 교체하세요.");
        }
    }

    /** Altcha 위젯이 GET 으로 호출 (challengeurl) */
    @GetMapping("/challenge")
    public Altcha.Challenge challenge() throws Exception {
        Altcha.ChallengeOptions opts = new Altcha.ChallengeOptions()
                .hmacKey(props.getHmacKey())
                .maxNumber(props.getMaxNumber())
                .expiresInSeconds(props.getExpiresSeconds());

        // Challenge 객체를 그대로 반환 → Jackson 이 위젯이 소비하는
        // { algorithm, challenge, maxnumber, salt, signature } JSON 으로 직렬화.
        return Altcha.createChallenge(opts);
    }
}
