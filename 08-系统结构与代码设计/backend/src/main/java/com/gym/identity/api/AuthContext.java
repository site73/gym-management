package com.gym.identity.api;

/**
 * 登录会话上下文（由鉴权拦截器写入，供各模块 Controller 读取）。
 *
 * <p>作用：让"会员只能看自己的数据"成为**框架级约束**，而不是靠前端自觉。
 */
public final class AuthContext {

    private static final ThreadLocal<AuthSession> HOLDER = new ThreadLocal<>();

    private AuthContext() {}

    public static void set(AuthSession session) { HOLDER.set(session); }

    public static void clear() { HOLDER.remove(); }

    public static AuthSession current() {
        AuthSession s = HOLDER.get();
        if (s == null) throw AuthException.unauthorized("未登录或登录已过期");
        return s;
    }

    /** 要求门店后台角色 */
    public static AuthSession requireStaff() {
        AuthSession s = current();
        if (!s.isStaff()) throw AuthException.forbidden("该操作仅限门店后台账号");
        return s;
    }

    /** 要求管理员角色 */
    public static AuthSession requireAdmin() {
        AuthSession s = current();
        if (!s.isAdmin()) throw AuthException.forbidden("该操作仅限管理员");
        return s;
    }

    /**
     * 归一化会员 ID：会员账号只能操作自己的数据。
     *
     * @param requested 前端传入的会员 ID（可为空）
     */
    public static Long effectiveMemberId(Long requested) {
        AuthSession s = current();
        if (s.isMember()) {
            if (s.memberId() == null) throw AuthException.forbidden("当前账号未绑定会员档案");
            return s.memberId();
        }
        return requested;
    }

    /** 会员账号校验：不允许访问其他会员的数据 */
    public static void assertSelfOrStaff(Long memberId) {
        AuthSession s = current();
        if (s.isMember() && !s.memberId().equals(memberId)) {
            throw AuthException.forbidden("只能查看本人数据");
        }
    }
}
