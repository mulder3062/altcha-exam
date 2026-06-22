# 간편인증 무차별 대입 방어 파일럿 (Altcha PoW)

> Next.js(Frontend) + Spring Boot(Backend) 파일럿 구현 가이드
> 작성일: 2026-06-19

---

## 1. 목적 & 배경

검진결과 조회를 위해 고객은 성명/병원등록번호/휴대폰번호 3가지를 입력 후 Submit을 하게된다.

3개 정보의 엔트로피가 낮아(병원등록번호가 순차적, 성명·휴대폰은 유출/사회공학으로 확보 가능) 무차별 대입 + 식별자 열거(enumeration)에 취약하다.
인증 폼 앞단에 PoW(Proof of Work) 챌린지를 두어 자동화 공격 비용을 크게 높여야 한다.

- PoW는 "자동화 비용 상승"이 목적, 실제 차단(Rate Limit, 식별자별 시도 제한)은 백엔드가 담당

---

## 2. 제약 조건

| 항목 | 제약 |
|---|---|
| 외부 클라우드 서비스 | **사용 불가** (Cloudflare Turnstile, hCaptcha, reCAPTCHA 등 전부 제외) |
| 인프라 | **별도 서버/프로세스 추가 불가** (자체 서버 1대) |
| 호스팅 | 자체 서버 (Vercel 아님) |
| 상태 관리 | **인메모리** (Redis 등 외부 스토어 없이 단순하게) |

---

## 3. 솔루션 선정 요약 — 왜 Altcha인가

조사 결과 위 제약(외부 통신 0 · 별도 서버 0 · Spring Boot 임베드)을 만족하는 후보로 Altcha를 선택했다.

**Altcha를 선택한 이유:**

| 항목 | Altcha | Cap |
|---|---|---|
| Java/Spring Boot 지원 | **공식 라이브러리 `org.altcha:altcha`** + 예제 제공 | 공식 없음 → 직접 포팅 필요 |
| 외부 통신 | **없음** (로컬 HMAC/해시 연산) | 없음 |
| 상태 관리 | HMAC 서명(stateless) → 저장 최소화 | legacy 방식은 인메모리 Map 필요 |
| 라이선스 | **MIT** (위젯·Java 라이브러리 모두) | Apache 2.0 |

- **라이선스**: `altcha`(위젯, MIT) / `altcha-lib-java`(MIT) — 표준 MIT, 추가 조항 없음. 상업적 납품 자유, 의무는 라이선스 고지 유지뿐.
- **외부 통신**: `altcha-lib-java`는 `createChallenge`/`verifySolution` 모두 로컬 연산. 네트워크 I/O 코드 없음. 런타임 의존성은 `org.json` 하나.
  - 단, **Sentinel(클라우드 스팸필터)은 사용하지 않음** → 외부 통신 0 유지
  - 프론트 위젯은 **CDN이 아니라 npm으로 번들** + `challengeurl`을 자체 서버로 지정 → 외부 통신 0

---

## 4. 아키텍처 & 플로우

```
[ Next.js (Frontend) ]                         [ Spring Boot (Backend, 1대 · 인메모리) ]

① 화면 진입
② Altcha 위젯 로드 ───── GET challengeurl ─────▶ ③ /captcha/challenge
                                                    Altcha.createChallenge()
                ◀──────── challenge(JSON) ──────────  (HMAC 서명, TTL 5분)
④ 위젯이 PoW 풀이
   (SHA-256 nonce 탐색, WASM)
⑤ 간편인증 폼 제출 ──── 성명+등록번호+휴대폰 ────▶ ⑥ API
   + altcha payload                                  (1) Altcha.verifySolution() 검증
                                                      (2) payload 재사용 여부 확인 (1회용)
                                                      (3) 3개 정보 일치 확인
                ◀──────── 통과 / 거부 ──────────────  
```

- **요청**(challenge 요청, 폼 제출)은 실선, **응답**은 백엔드가 반환
- `challenge` 발급과 `verify` 검증 모두 **같은 HMAC secret**으로 자체 서버에서 완결 → 외부·별도 서버 불필요

---

## 5. Backend (Spring Boot)

> 코드의 빌더/메서드명은 채택할 라이브러리 버전에 맞춰 [altcha-lib-java README](https://github.com/altcha-org/altcha-lib-java)로 최종 확인할 것. 아래는 v1(hashcash·SHA-256, 위젯 기본 호환) 기준 예시.

### 5.1 의존성 (Gradle)

Maven 의존성 참고:
```xml
<dependency>
    <groupId>org.altcha</groupId>
    <artifactId>altcha</artifactId>
    <version>2.0.1</version>
</dependency>
```

### 5.2 설정 — HMAC Secret

```yaml
# application.yml
altcha:
  hmac-key: ${ALTCHA_HMAC_KEY}   # 환경변수로 주입. 충분히 긴 랜덤 문자열. 절대 커밋 금지.
  max-number: 100000             # PoW 난이도(클수록 어려움). 파일럿은 50,000~200,000 범위에서 튜닝
  expires-seconds: 300           # 챌린지 TTL 5분
```

### 5.3 챌린지 발급 엔드포인트

예시코드:
```java
@RestController
@RequestMapping("/captcha")
public class CaptchaController {

    @Value("${altcha.hmac-key}")        private String hmacKey;
    @Value("${altcha.max-number}")      private long maxNumber;
    @Value("${altcha.expires-seconds}") private int expiresSeconds;

    /** Altcha 위젯이 GET 으로 호출 (challengeurl) */
    @GetMapping("/challenge")
    public Map<String, Object> challenge() throws Exception {
        Altcha.ChallengeOptions opts = new Altcha.ChallengeOptions()
                .setHmacKey(hmacKey)
                .setMaxNumber(maxNumber)
                .setExpires(Instant.now().plusSeconds(expiresSeconds));

        Altcha.Challenge c = Altcha.createChallenge(opts);
        // 반환 형식은 위젯이 그대로 소비: { algorithm, challenge, maxnumber, salt, signature }
        return Map.of(
                "algorithm", c.algorithm,
                "challenge", c.challenge,
                "maxnumber", c.maxnumber,
                "salt",      c.salt,
                "signature", c.signature
        );
    }
}
```

### 5.4 솔루션 검증 + 재사용(Replay) 방지

Altcha는 **솔루션 검증만** 할 뿐, 같은 payload의 재사용을 막지 않는다. 1회용 보장을 위해 **사용된 payload를 인메모리에 기록**한다. (기존 IP Rate Limit의 `ConcurrentHashMap` + 만료 정리 패턴과 동일)

```java
@Component
public class AltchaVerifier {

    @Value("${altcha.hmac-key}") private String hmacKey;

    // 사용된 솔루션 기록 (payload 해시 → 만료시각). @Scheduled 로 주기 정리.
    private final Map<String, Instant> usedSolutions = new ConcurrentHashMap<>();

    public boolean verify(String altchaPayloadBase64) throws Exception {
        // 1) PoW + HMAC 서명 + 만료(checkExpires=true) 로컬 검증
        boolean ok = Altcha.verifySolution(altchaPayloadBase64, hmacKey, true);
        if (!ok) return false;

        // 2) 재사용 방지 (1회용)
        String fingerprint = sha256(altchaPayloadBase64);
        Instant prev = usedSolutions.putIfAbsent(fingerprint, Instant.now().plusSeconds(300));
        return prev == null;   // 이미 쓰인 payload면 거부
    }

    @Scheduled(fixedRate = 60_000)
    public void evictExpired() {
        Instant now = Instant.now();
        usedSolutions.entrySet().removeIf(e -> e.getValue().isBefore(now));
    }

    private String sha256(String s) { /* MessageDigest SHA-256 hex */ }
}
```

### 5.5 API에 적용

```java
@PostMapping("/verify")          // 예약조회/문진/수납 진입 인증
public ResponseEntity<?> verifyBType(@RequestBody BTypeAuthRequest req) throws Exception {

    // (0) 상단 IP Rate Limit 통과 후 진입 (기존 흐름)

    // (1) Altcha PoW 검증 + 재사용 방지
    if (!altchaVerifier.verify(req.getAltcha())) {
        return ResponseEntity.status(400).body("캡차 검증 실패");
    }

    // (2) 간편인증 3개 정보 일치 확인
    boolean matched = authService.match(req.getName(), req.getHospitalNo(), req.getPhone());

    // (3) enumeration 방어: 어떤 필드가 틀렸는지 노출하지 말 것 (동일한 일반 메시지)
    if (!matched) {
        return ResponseEntity.status(401).body("인증 정보가 일치하지 않습니다.");
    }
    return ResponseEntity.ok(/* 세션/토큰 발급 */);
}
```

---

## 6. Frontend (Next.js)

### 6.1 설치

```bash
npm i altcha
```

> CDN 로드가 아니라 npm 번들로 포함 → 외부 통신 0.

### 6.2 위젯 컴포넌트 (Client Component)

Altcha 위젯은 Web Component(`<altcha-widget>`)이므로 브라우저 전용. SSR 비활성화로 로드한다.

```tsx
// components/AltchaWidget.tsx
'use client';
import { useEffect, useRef } from 'react';
import 'altcha'; // web component (<altcha-widget>) 등록

type Props = { onVerified: (payload: string) => void };

export default function AltchaWidget({ onVerified }: Props) {
  const ref = useRef<HTMLElement>(null);

  useEffect(() => {
    const el = ref.current;
    const handler = (ev: any) => {
      if (ev.detail?.state === 'verified') onVerified(ev.detail.payload);
    };
    el?.addEventListener('statechange', handler);
    return () => el?.removeEventListener('statechange', handler);
  }, [onVerified]);

  // challengeurl 을 자체 백엔드로 지정 (Next rewrites 프록시 또는 직접 URL)
  return <altcha-widget ref={ref} challengeurl="/api/captcha/challenge" />;
}
```

JSX 타입 에러 방지를 위한 선언:

```ts
// types/altcha.d.ts
declare namespace JSX {
  interface IntrinsicElements {
    'altcha-widget': any;
  }
}
```

### 6.3 폼 제출

```tsx
'use client';
import { useState } from 'react';
import AltchaWidget from '@/components/AltchaWidget';

export default function BTypeAuthForm() {
  const [altcha, setAltcha] = useState('');

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!altcha) return alert('캡차 인증을 완료해 주세요.');
    const f = new FormData(e.currentTarget);
    const res = await fetch('/api/b-type/verify', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: f.get('name'),
        hospitalNo: f.get('hospitalNo'),
        phone: f.get('phone'),
        altcha, // 위젯에서 받은 payload
      }),
    });
    // 결과 처리 (성공/실패 메시지는 일반화)
  }

  return (
    <form onSubmit={onSubmit}>
      <input name="name" placeholder="성명" />
      <input name="hospitalNo" placeholder="병원등록번호" />
      <input name="phone" placeholder="휴대폰번호" />
      <AltchaWidget onVerified={setAltcha} />
      <button type="submit">확인</button>
    </form>
  );
}
```

> `challengeurl="/api/captcha/challenge"` 는 Next.js `rewrites`로 Spring Boot(`/captcha/challenge`)에 프록시하거나, 자체 서버 절대 URL을 직접 지정.

---

## 7. Replay(재사용) 방지 설계 요약

- Altcha는 **검증만** 하고 1회용을 보장하지 않음 → 직접 처리 필요
- 인메모리 `ConcurrentHashMap<payload해시, 만료시각>` 에 사용된 payload 기록, `@Scheduled` 로 만료 정리
- TTL은 챌린지 만료(5분)와 동일하게 두면 충분 (만료 후엔 어차피 검증 실패)
- **1대 서버 + 인메모리 전제**이므로 Redis 불필요. 서버 재시작 시 기록이 사라지지만, 사용자는 위젯을 다시 풀면 되어 문제 없음

---

## 8. 파일럿 테스트 시나리오 / 체크리스트

- [ ] **정상 흐름**: 위젯 자동 풀이 → 폼 제출 → 3개 정보 일치 → 통과
- [ ] **PoW 미완료 제출**: altcha payload 없이 제출 → 400
- [ ] **위조 payload**: 임의/변조된 payload → `verifySolution` 실패 → 400
- [ ] **만료 테스트**: 챌린지 발급 후 5분 경과 → 검증 실패
- [ ] **재사용 공격**: 동일 payload로 2회 제출 → 1회만 통과, 2회차 거부
- [ ] **enumeration 방어**: 등록번호만 틀림 / 휴대폰만 틀림 → **동일한 일반 메시지**인지 확인
- [ ] **난이도 체감**: `maxNumber` 값별 저사양 단말 풀이 시간 측정(UX 허용 범위 튜닝)
- [ ] **외부 통신 0 검증**: 브라우저 네트워크 탭 + 서버 아웃바운드 로그에 Altcha 외부 호출이 없는지 확인
- [ ] **부하/우회**: IP Rate Limit과 병행 동작, IP 변경 시 식별자별 시도 제한 동작 확인

---

## 9. 운영 / 보안 주의사항

- **HMAC secret 관리**: 환경변수로 주입, 소스/리포지토리에 커밋 금지. 유출 시 PoW 우회 가능 → 교체 절차 마련
- **난이도(maxNumber) 튜닝**: 너무 높으면 저사양 단말 UX 저하, 너무 낮으면 방어력 약화. 파일럿에서 단말별 측정 후 결정
- **PoW는 보조 수단**: 결제(예약금수납)가 끼므로 **SMS OTP 추가**를 별도 트랙으로 검토 권장
- **에러 메시지 일반화**: 어느 필드가 틀렸는지 노출 금지(서버 응답 시간 차이도 일정하게)
- **Sentinel 미사용**: 외부 통신 0 유지를 위해 Altcha 클라우드 스팸필터(Sentinel)는 사용하지 않음
- **OSS 고지**: 발주사 오픈소스 목록(BOM)에 `altcha`(MIT), `altcha-lib-java`(MIT) 등재 + LICENSE 사본 첨부. 채택 버전 태그의 LICENSE 최종 대조

---

## 10. 참고 링크

- altcha-lib-java (공식 Java 라이브러리, MIT): https://github.com/altcha-org/altcha-lib-java
- altcha 위젯 (npm, MIT): https://www.npmjs.com/package/altcha
- Altcha 서버 연동 문서: https://altcha.org/docs/v2/server-integration/
- Maven Central (org.altcha:altcha): https://central.sonatype.com/artifact/org.altcha/altcha
- Altcha 공식 사이트: https://altcha.org/
