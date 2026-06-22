/** @type {import('next').NextConfig} */
const nextConfig = {
  // /api/* 요청을 Spring Boot 백엔드로 프록시.
  // → 브라우저 입장에서는 동일 출처이므로 CORS 불필요 + Retry-After 헤더 그대로 노출.
  async rewrites() {
    const backend = process.env.BACKEND_URL || 'http://localhost:8080';
    return [
      { source: '/api/captcha/:path*', destination: `${backend}/captcha/:path*` },
      { source: '/api/auth/:path*', destination: `${backend}/auth/:path*` },
    ];
  },
};

module.exports = nextConfig;
