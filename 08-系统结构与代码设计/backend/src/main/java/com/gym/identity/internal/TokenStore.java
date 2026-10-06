package com.gym.identity.internal;

import com.gym.identity.api.AuthSession;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 令牌存储（内存实现）。
 *
 * <p>大作业场景下用内存会话足够；生产应替换为 Redis 或 JWT（无状态）。
 * 说明：服务重启后令牌失效，需重新登录 —— 这一点已在运行手册中注明。
 */
@Component
public class TokenStore {

    /** 会话有效期（小时） */
    public static final int TTL_HOURS = 8;

    private final Map<String, AuthSession> sessions = new ConcurrentHashMap<>();

    public AuthSession create(Long userId, String username, String displayName,
                              java.util.List<String> roles, String role, Long memberId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        AuthSession session = new AuthSession(token, userId, username, displayName,
                roles, role, memberId, LocalDateTime.now().plusHours(TTL_HOURS));
        sessions.put(token, session);
        return session;
    }

    public Optional<AuthSession> get(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        AuthSession s = sessions.get(token);
        if (s == null) return Optional.empty();
        if (s.expiresAt().isBefore(LocalDateTime.now())) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.of(s);
    }

    public void remove(String token) {
        if (token != null) sessions.remove(token);
    }

    public int size() { return sessions.size(); }
}
