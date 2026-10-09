package com.gym.identity.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录会话（对外契约类型）。
 *
 * @param token       访问令牌（前端通过 X-Token 请求头携带）
 * @param userId      系统用户 ID
 * @param username    登录名
 * @param displayName 显示名
 * @param roles       角色编码（member / manager / admin …）
 * @param role        归一化角色：MEMBER（会员端）/ STAFF（门店后台）
 * @param memberId    关联的会员 ID（仅会员账号有值）
 * @param coachId     关联的教练档案 ID（仅教练账号有值）
 * @param expiresAt   过期时间
 */
public record AuthSession(
        String token,
        Long userId,
        String username,
        String displayName,
        List<String> roles,
        String role,
        Long memberId,
        Long coachId,
        LocalDateTime expiresAt) {

    public boolean isMember() { return "MEMBER".equals(role); }
    public boolean isStaff() { return "STAFF".equals(role); }
    public boolean isCoach() { return "COACH".equals(role); }
    public boolean isAdmin() { return roles != null && roles.contains("admin"); }

    /** 对外返回的用户信息（不含令牌） */
    public UserInfo toUserInfo() {
        return new UserInfo(userId, username, displayName, roles, role, memberId, coachId, isAdmin());
    }

    public record UserInfo(Long userId, String username, String displayName,
                           List<String> roles, String role, Long memberId, Long coachId, boolean admin) {}
}
