package com.gym.identity.application;

import com.gym.identity.api.AuthException;
import com.gym.identity.api.AuthFacade;
import com.gym.identity.api.AuthSession;
import com.gym.identity.internal.SysUserEntity;
import com.gym.identity.internal.SysUserRepository;
import com.gym.identity.internal.TokenStore;
import com.gym.membership.api.MembershipFacade;
import com.gym.shared.audit.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /** 密码最小长度 */
    public static final int MIN_PASSWORD_LENGTH = 6;

    private final SysUserRepository userRepository;
    private final TokenStore tokenStore;
    private final AuditLogger auditLogger;
    private final MembershipFacade membershipFacade;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthAppService(SysUserRepository userRepository, TokenStore tokenStore,
                          AuditLogger auditLogger, MembershipFacade membershipFacade) {
        this.userRepository = userRepository;
        this.tokenStore = tokenStore;
        this.auditLogger = auditLogger;
        this.membershipFacade = membershipFacade;
    }

    /**
     * 会员自助注册：一次事务内创建「登录账号 + 会员档案 + 角色绑定」，并直接签发令牌。
     *
     * <p>账号与会员档案通过 {@code member.user_id} 关联，注册后即可在会员端自助选课 / 退课。
     */
    @Override
    @Transactional
    public AuthSession register(String username, String password, String name, String phone) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (password == null || password.trim().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("密码至少 " + MIN_PASSWORD_LENGTH + " 位");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("姓名不能为空");
        }
        String uname = username.trim();
        if (userRepository.findByUsername(uname).isPresent()) {
            throw new IllegalArgumentException("用户名已被占用：" + uname);
        }

        var user = userRepository.save(
                new SysUserEntity(uname, encoder.encode(password), name.trim()));
        userRepository.bindRole(user.getId(), "member");
        var member = membershipFacade.registerMember(name, phone, user.getId());

        auditLogger.log("auth.register", "sys_user", user.getId(),
                "memberNo=" + member.memberNo() + ", name=" + member.name());
        log.info("[auth] 新会员注册成功：{} → {}（会员 ID {}）", uname, member.memberNo(), member.id());

        return tokenStore.create(user.getId(), user.getUsername(), user.getDisplayName(),
                List.of("member"), "MEMBER", member.id());
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
