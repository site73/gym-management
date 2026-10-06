package com.gym.identity.api;

/** 身份认证契约。 */
public interface AuthFacade {

    /** 登录：校验账号密码并签发令牌 */
    AuthSession login(String username, String password);

    /** 校验令牌并返回会话；无效或过期抛 401 */
    AuthSession resolve(String token);

    /** 退出登录（令牌失效） */
    void logout(String token);
}
