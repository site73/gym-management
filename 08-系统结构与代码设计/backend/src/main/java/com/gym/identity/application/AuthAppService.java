package com.gym.identity.application;

import com.gym.identity.api.AuthException;
import com.gym.identity.api.AuthFacade;
import com.gym.identity.api.AuthSession;
import com.gym.identity.internal.SysUserRepository;
import com.gym.identity.internal.TokenStore;
import com.gym.shared.audit.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 登录用例：校验凭据 → 签发令牌；并负责令牌校验与注销。
 *
 * <p>角色归一化规则：
 * <ul>
 *   <li>含 {@code member} → MEMBER（进入会员端）</li>
 *   <li>其他（manager / staff_sales / pt / finance / admin）→ STAFF（进入门店后台）</li>
 * </ul>
 */
@Service
public class AuthAppService implements AuthFacade {

    private static final Logger log = LoggerFactory.getLogger(AuthAppService.class);

    private final SysUserRepository userRepository;
    private final TokenStore tokenStore;
    private final AuditLogger auditLogger;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthAppService(SysUserRepository userRepository, TokenStore tokenStore, AuditLogger auditLogger) {
        this.userRepository = userRepository;
        this.tokenStore = tokenStore;
        this.auditLogger = auditLogger;
    }

    @Override
    public AuthSession login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw AuthException.unauthorized("请输入用户名和密码");
        }
        var user = userRepository.findByUsername(username.trim()).orElse(null);
        // 统一提示，避免暴露"用户是否存在"
        if (user == null || !encoder.matches(password, user.getPasswordHash())) {
            log.warn("[auth] 登录失败：username={}", username);
            auditLogger.log("auth.loginFailed", "sys_user", null, "username=" + username);
            throw AuthException.unauthorized("用户名或密码错误");
        }
        if (!"active".equals(user.getStatus())) {
            throw AuthException.unauthorized("账号已被禁用，请联系管理员");
        }

        List<String> roles = userRepository.findRoleCodes(user.getId());
        String role = roles.contains("member") ? "MEMBER" : "STAFF";
        Long memberId = "MEMBER".equals(role) ? userRepository.findMemberIdByUserId(user.getId()) : null;

        AuthSession session = tokenStore.create(user.getId(), user.getUsername(),
                user.getDisplayName(), roles, role, memberId);
        auditLogger.log("auth.login", "sys_user", user.getId(), "role=" + role);
        log.info("[auth] 登录成功：{}（{}）", user.getUsername(), role);
        return session;
    }

    @Override
    public AuthSession resolve(String token) {
        return tokenStore.get(token)
                .orElseThrow(() -> AuthException.unauthorized("登录已过期，请重新登录"));
    }

    @Override
    public void logout(String token) {
        tokenStore.get(token).ifPresent(s ->
                auditLogger.log("auth.logout", "sys_user", s.userId(), s.username()));
        tokenStore.remove(token);
    }
}
