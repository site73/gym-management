package com.gym.identity.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 账号视图（仅超级管理员可见）。
 *
 * <p><b>关于密码</b>：密码以 BCrypt 哈希存储，**不可逆**，系统不提供"查看明文密码"的能力。
 * 本视图给出的是密码状态（是否被重置过、何时重置），配合"重置密码"功能使用：
 * 重置后新密码会随响应返回，因此管理员始终能掌握账号的可登录凭据，
 * 同时不破坏密码存储的安全前提。
 */
public record AccountView(
        Long id,
        String username,
        String displayName,
        List<String> roles,
        /** 归一化角色：会员 / 教练 / 门店后台 / 超级管理员 */
        String roleCn,
        /** 数据归属：绑定的会员或教练 */
        Long memberId,
        String memberName,
        Long coachId,
        String coachName,
        String status,
        String statusCn,
        boolean admin,
        boolean superAdmin,
        LocalDateTime createdAt,
        LocalDateTime passwordResetAt,
        /** 密码状态提示（不含明文密码） */
        String passwordHint) {

    /** 账号类型中文名 */
    public static String roleCnOf(List<String> roles) {
        if (roles == null) return "未知";
        if (roles.contains("super_admin")) return "超级管理员";
        if (roles.contains("admin")) return "系统管理员";
        if (roles.contains("coach")) return "教练";
        if (roles.contains("member")) return "会员";
        return "门店后台";
    }

    public static String statusCnOf(String status) {
        return "active".equals(status) ? "启用" : "停用";
    }

    public static String passwordHintOf(LocalDateTime resetAt) {
        if (resetAt == null) {
            return "未重置过（演示账号为初始密码；会员可能已自行修改）";
        }
        return "已于 " + resetAt.toString().replace('T', ' ').substring(0, 16) + " 重置为默认密码";
    }

    /** 是否演示内置账号（可安全展示初始密码） */
    public boolean builtin() {
        return "member1".equals(username) || "member2".equals(username)
                || "manager".equals(username) || "admin".equals(username)
                || "superadmin".equals(username)
                || (username != null && username.startsWith("coach") && username.length() == 6);
    }
}
