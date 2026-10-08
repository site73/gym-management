package com.gym.identity.api;

/** 身份认证契约。 */
public interface AuthFacade {

    /** 登录：校验账号密码并签发令牌 */
    AuthSession login(String username, String password);

    /**
     * 会员自助注册：创建登录账号 + 会员档案并直接签发令牌（注册即登录）。
     *
     * @param username 登录名（手机号或自定义，需唯一）
     * @param password 密码（至少 6 位）
     * @param name     会员姓名
     * @param phone    手机号（可空）
     */
    AuthSession register(String username, String password, String name, String phone);

    /** 校验令牌并返回会话；无效或过期抛 401 */
    AuthSession resolve(String token);

    /** 退出登录（令牌失效） */
    void logout(String token);
}
