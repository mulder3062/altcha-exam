'use client';

import { useEffect, useRef, useState } from 'react';
import AltchaWidget from './AltchaWidget';

type Result =
  | { kind: 'idle' }
  | { kind: 'success'; message: string }
  | { kind: 'error'; message: string };

export default function LoginForm() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [altcha, setAltcha] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<Result>({ kind: 'idle' });

  // 위젯 재마운트 키. 제출할 때마다 증가시켜 새 PoW payload 를 발급한다.
  // (백엔드 replay 방지로 1회용 payload 는 재사용 불가 → 매 시도마다 새 챌린지 필요)
  const [widgetKey, setWidgetKey] = useState(0);

  // 차단 카운트다운(초). 0 이면 차단 아님.
  const [remaining, setRemaining] = useState(0);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // 드리프트 방지: 종료 목표 시각을 잡고 매 틱마다 남은 초를 재계산한다.
  function startCountdown(seconds: number) {
    if (timerRef.current) clearInterval(timerRef.current);
    const deadline = Date.now() + seconds * 1000;
    setRemaining(seconds);

    timerRef.current = setInterval(() => {
      const left = Math.max(0, Math.round((deadline - Date.now()) / 1000));
      setRemaining(left);
      if (left <= 0 && timerRef.current) {
        clearInterval(timerRef.current);
        timerRef.current = null;
      }
    }, 1000);
  }

  useEffect(() => {
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, []);

  const blocked = remaining > 0;

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (blocked) return;
    if (!altcha) {
      setResult({ kind: 'error', message: '자동입력 방지 인증을 완료해 주세요.' });
      return;
    }

    setSubmitting(true);
    setResult({ kind: 'idle' });
    try {
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password, altcha }),
      });

      if (res.status === 429) {
        // 차단됨: Retry-After(초)로 카운트다운 시작
        const retryAfter = Number(res.headers.get('Retry-After')) || 0;
        startCountdown(retryAfter);
        setResult({ kind: 'idle' });
        return;
      }

      const data = await res.json().catch(() => ({}));
      if (res.ok) {
        setResult({ kind: 'success', message: data.message ?? '로그인 성공' });
      } else {
        // 400(캡차 실패) / 401(자격증명 불일치) — 일반화된 메시지만 표시
        setResult({ kind: 'error', message: data.message ?? '요청을 처리할 수 없습니다.' });
      }
    } catch {
      setResult({ kind: 'error', message: '네트워크 오류가 발생했습니다.' });
    } finally {
      setSubmitting(false);
      // 이번 제출에 쓴 payload 는 1회용이라 소진됨 → 위젯을 재마운트해
      // 다음 시도에 쓸 새 PoW payload 를 자동으로(auto="onload") 발급한다.
      setAltcha('');
      setWidgetKey((k) => k + 1);
    }
  }

  return (
    <div className="card">
      <h1>로그인</h1>
      <p className="sub">무차별 대입 방어 데모 · 계정: demo / password123</p>

      <form onSubmit={onSubmit}>
        <div className="field">
          <label htmlFor="username">아이디</label>
          <input
            id="username"
            name="username"
            autoComplete="username"
            value={username}
            disabled={blocked || submitting}
            onChange={(e) => setUsername(e.target.value)}
          />
        </div>

        <div className="field">
          <label htmlFor="password">비밀번호</label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            value={password}
            disabled={blocked || submitting}
            onChange={(e) => setPassword(e.target.value)}
          />
        </div>

        <AltchaWidget key={widgetKey} onVerified={setAltcha} onReset={() => setAltcha('')} />

        <button type="submit" disabled={blocked || submitting}>
          {submitting ? '확인 중...' : '로그인'}
        </button>
      </form>

      {blocked && (
        <div className="msg blocked">
          시도 횟수 초과 · <strong>{remaining}초</strong> 후 재시도 가능
        </div>
      )}

      {!blocked && result.kind === 'error' && (
        <div className="msg error">{result.message}</div>
      )}

      {!blocked && result.kind === 'success' && (
        <div className="msg success">{result.message} 🎉</div>
      )}
    </div>
  );
}
