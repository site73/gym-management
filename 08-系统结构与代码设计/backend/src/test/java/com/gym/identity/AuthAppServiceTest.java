package com.gym.identity;

import com.gym.identity.api.AuthException;
import com.gym.identity.api.AuthFacade;
import com.gym.identity.api.AuthSession;
import com.gym.identity.application.AuthAppService;
import com.gym.identity.internal.SysUserEntity;
import com.gym.identity.internal.SysUserRepository;
import com.gym.identity.internal.TokenStore;
import com.gym.shared.audit.AuditLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * 登录与角色映射单元测试。
 *
 * <p>覆盖：密码校验、禁用账号、角色归一化（member→MEMBER，manager/admin→STAFF）、
 * 会员账号与档案绑定、令牌签发/失效/注销、错误提示不泄露账号是否存在。
 */
class AuthAppServiceTest {

    private final SysUserRepository repo = mock(SysUserRepository.class);
    private final AuditLogger audit = mock(AuditLogger.class);
    private final TokenStore tokenStore = new TokenStore();
    private final AuthFacade auth = new AuthAppService(repo, tokenStore, audit);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private SysUserEntity stub(String username, String rawPassword, String status, List<String> roles) {
        SysUserEntity u = new SysUserEntity(username, encoder.encode(rawPassword), username + " 显示名");
        u.setStatus(status);
        when(repo.findByUsername(username)).thenReturn(Optional.of(u));
        when(repo.findRoleCodes(any())).thenReturn(roles);
        return u;
    }

    @Test
    @DisplayName("会员账号登录成功并归一化为 MEMBER，且绑定会员 ID")
    void member_login_maps_to_member_role() {
        stub("member1", "123456", "active", List.of("member"));
        when(repo.findMemberIdByUserId(any())).thenReturn(1L);

        AuthSession s = auth.login("member1", "123456");

        assertThat(s.role()).isEqualTo("MEMBER");
        assertThat(s.isMember()).isTrue();
        assertThat(s.memberId()).isEqualTo(1L);
        assertThat(s.token()).isNotBlank();
        assertThat(s.expiresAt()).isAfter(java.time.LocalDateTime.now());
    }

    @Test
    @DisplayName("店长账号归一化为 STAFF，且不绑定会员档案")
    void manager_login_maps_to_staff_role() {
        stub("manager", "123456", "active", List.of("manager"));

        AuthSession s = auth.login("manager", "123456");

        assertThat(s.role()).isEqualTo("STAFF");
        assertThat(s.memberId()).isNull();
        assertThat(s.isAdmin()).isFalse();
        verify(repo, never()).findMemberIdByUserId(any());
    }

    @Test
    @DisplayName("管理员账号 isAdmin 为真（可执行重置）")
    void admin_flag() {
        stub("admin", "123456", "active", List.of("admin"));
        assertThat(auth.login("admin", "123456").isAdmin()).isTrue();
    }

    @Test
    @DisplayName("密码错误返回 401 语义，且提示不区分账号是否存在")
    void wrong_password() {
        stub("member1", "123456", "active", List.of("member"));

        assertThatThrownBy(() -> auth.login("member1", "bad"))
                .isInstanceOf(AuthException.class)
                .hasMessage("用户名或密码错误");
        assertThatThrownBy(() -> auth.login("nobody", "123456"))
                .isInstanceOf(AuthException.class)
                .hasMessage("用户名或密码错误");
    }

    @Test
    @DisplayName("空用户名或密码直接拒绝")
    void blank_input() {
        assertThatThrownBy(() -> auth.login("", "")).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> auth.login("member1", null)).isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("禁用账号不可登录")
    void disabled_account() {
        stub("member1", "123456", "disabled", List.of("member"));

        assertThatThrownBy(() -> auth.login("member1", "123456"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("禁用");
    }

    @Test
    @DisplayName("令牌可校验；注销后失效；无效令牌抛 401")
    void token_lifecycle() {
        stub("manager", "123456", "active", List.of("manager"));
        AuthSession s = auth.login("manager", "123456");

        assertThat(auth.resolve(s.token()).username()).isEqualTo("manager");

        auth.logout(s.token());
        assertThatThrownBy(() -> auth.resolve(s.token())).isInstanceOf(AuthException.class);

        assertThatThrownBy(() -> auth.resolve("not-a-token")).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> auth.resolve(null)).isInstanceOf(AuthException.class);
    }
}
