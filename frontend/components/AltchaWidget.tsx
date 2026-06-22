'use client';

import { useEffect, useRef, useState } from 'react';

type Props = {
  onVerified: (payload: string) => void;
  onReset?: () => void;
};

/**
 * Altcha PoW 위젯.
 * challengeurl 을 자체 백엔드(Next rewrites 프록시)로 지정해 외부 통신을 만들지 않는다.
 * (checkup-b-type-altcha-pilot.md §6.2)
 */
export default function AltchaWidget({ onVerified, onReset }: Props) {
  const ref = useRef<HTMLElement>(null);
  const [ready, setReady] = useState(false);

  // web component(<altcha-widget>)는 브라우저 전용(customElements 필요)이므로
  // SSR/프리렌더를 피해 클라이언트에서만 동적 로드한다. CDN 아님 → 외부 통신 0.
  useEffect(() => {
    let mounted = true;
    import('altcha').then(() => {
      if (mounted) setReady(true);
    });
    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    if (!ready) return;
    const el = ref.current;
    if (!el) return;

    const handler = (ev: any) => {
      const state = ev.detail?.state;
      if (state === 'verified' && ev.detail?.payload) {
        onVerified(ev.detail.payload);
      } else if (state !== 'verified') {
        // 만료/에러/초기화 시 부모의 payload 무효화
        onReset?.();
      }
    };

    el.addEventListener('statechange', handler);
    return () => el.removeEventListener('statechange', handler);
  }, [ready, onVerified, onReset]);

  if (!ready) {
    return <div style={{ fontSize: 13, color: '#94a3b8', margin: '6px 0' }}>인증 위젯 로딩 중...</div>;
  }

  return (
    <altcha-widget
      ref={ref}
      hidefooter
      hidelogo
      challengeurl="/api/captcha/challenge"
      strings='{"label":"자동입력 방지 인증","verified":"인증 완료","verifying":"확인 중...","waitAlert":"확인 중입니다, 잠시만 기다려 주세요..."}'
    />
  );
}
