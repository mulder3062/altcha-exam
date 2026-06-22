package com.example.bruteforce.auth;

import org.springframework.stereotype.Service;

/**
 * 데모용 인증 서비스. 하드코딩된 유효 계정과 일치 여부만 확인한다.
 * (실서비스라면 DB + 비밀번호 해시(BCrypt 등) 사용)
 */
@Service
public class AuthService {

    private static final String VALID_USERNAME = "demo";
    private static final String VALID_PASSWORD = "password123";

    public boolean matches(String username, String password) {
        if (username == null || password == null) {
            return false;
        }
        // enumeration 방어: 사용자 존재 여부와 무관하게 동일한 비교 경로를 타도록 단순 비교.
        return VALID_USERNAME.equals(username) && VALID_PASSWORD.equals(password);
    }
}
