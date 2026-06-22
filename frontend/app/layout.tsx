import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: '로그인 — 무차별 대입 방어 데모',
  description: 'Rate Limit + Altcha PoW 브루트포스 방어 예시',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
