package com.example.bruteforce.auth;

/**
 * 로그인 요청 바디.
 * altcha = 위젯에서 받은 PoW payload(base64).
 */
public record LoginRequest(String username, String password, String altcha) {
}
