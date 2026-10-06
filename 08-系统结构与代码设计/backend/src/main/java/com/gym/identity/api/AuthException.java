package com.gym.identity.api;

/**
 * 鉴权失败异常：
 * <ul>
 *   <li>认证失败（用户名密码错误 / 令牌无效）→ 401</li>
 *   <li>权限不足（角色不允许）→ 403</li>
 * </ul>
 */
public class AuthException extends RuntimeException {

    private final boolean forbidden;

    private AuthException(String message, boolean forbidden) {
        super(message);
        this.forbidden = forbidden;
    }

    /** 认证失败（401） */
    public static AuthException unauthorized(String message) {
        return new AuthException(message, false);
    }

    /** 权限不足（403） */
    public static AuthException forbidden(String message) {
        return new AuthException(message, true);
    }

    public boolean isForbidden() { return forbidden; }
}
