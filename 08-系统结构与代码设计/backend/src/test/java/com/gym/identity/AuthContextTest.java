package com.gym.identity;

import com.gym.identity.api.AuthContext;
import com.gym.identity.api.AuthException;
import com.gym.identity.api.AuthSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 会话上下文与权限判定单元测试（服务端强制的权限边界）。
 *
 * <p>这是"会员不能看别人数据"的关键防线，必须有测试守护。
 */
class AuthContextTest {

    private AuthSession member(long memberId) {
        return new AuthSession("t-member", 10L, "member1", "张三", List.of("member"),
                "MEMBER", memberId, LocalDateTime.now().plusHours(1));
    }

    private AuthSession staff(boolean admin) {
        return new AuthSession("t-staff", 20L, admin ? "admin" : "manager", "店长",
                admin ? List.of("admin") : List.of("manager"), "STAFF", null,
                LocalDateTime.now().plusHours(1));
    }

    @AfterEach
    void tearDown() { AuthContext.clear(); }

    @Test
    @DisplayName("未登录时读取上下文 → 401")
    void no_session() {
        assertThatThrownBy(AuthContext::current).isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("会员账号的会员 ID 被强制为本人（忽略前端传入）")
    void member_id_is_forced_to_self() {
        AuthContext.set(member(1L));
        assertThat(AuthContext.effectiveMemberId(999L)).isEqualTo(1L);
        assertThat(AuthContext.effectiveMemberId(null)).isEqualTo(1L);
    }

    @Test
    @DisplayName("门店后台可查询指定会员")
    void staff_can_query_any_member() {
        AuthContext.set(staff(false));
        assertThat(AuthContext.effectiveMemberId(999L)).isEqualTo(999L);
    }

    @Test
    @DisplayName("会员访问他人数据 → 403")
    void member_cannot_access_other_member() {
        AuthContext.set(member(1L));
        assertThatThrownBy(() -> AuthContext.assertSelfOrStaff(2L))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("只能查看本人数据");
    }

    @Test
    @DisplayName("门店后台访问任意会员数据 → 放行")
    void staff_can_access_any_member() {
        AuthContext.set(staff(false));
        AuthContext.assertSelfOrStaff(2L);   // 不抛异常即通过
    }

    @Test
    @DisplayName("会员调用门店后台接口 → 403")
    void member_cannot_call_staff_api() {
        AuthContext.set(member(1L));
        assertThatThrownBy(AuthContext::requireStaff)
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("门店后台");
    }

    @Test
    @DisplayName("店长非管理员，重置数据 → 403；管理员 → 放行")
    void reset_requires_admin() {
        AuthContext.set(staff(false));
        assertThatThrownBy(AuthContext::requireAdmin).isInstanceOf(AuthException.class);

        AuthContext.set(staff(true));
        assertThat(AuthContext.requireAdmin().isAdmin()).isTrue();
    }

    @Test
    @DisplayName("会话清理后不可再读取（防止线程复用串号）")
    void clear_removes_session() {
        AuthContext.set(member(1L));
        AuthContext.clear();
        assertThatThrownBy(AuthContext::current).isInstanceOf(AuthException.class);
    }
}
