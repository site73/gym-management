package com.gym.identity.api;

import java.util.List;
import java.util.Map;

/**
 * 账号管理契约（超级管理员专属）。
 *
 * <p>权限：所有方法都要求调用方具备 {@code super_admin} 角色，否则返回 403。
 *
 * <p><b>为什么没有"查看密码"接口</b>：密码用 BCrypt 哈希存储，单向不可逆，
 * 系统层面无法还原明文。若确实需要掌握某账号的登录凭据，请使用 {@link #resetPassword}：
 * 它把密码重置为默认值并返回新密码，等同于"重新拿到一个可用的密码"，
 * 且不会让全部账号都处于"明文可读"的高风险状态。
 */
public interface AccountAdminFacade {

    /** 账号总览（含角色、绑定的会员/教练、状态、密码重置时间） */
    List<AccountView> listAccounts();

    /** 单个账号 */
    AccountView accountOf(Long userId);

    /**
     * 重置密码为默认值，并返回新密码。
     *
     * <p>返回体形如 {@code {userId, username, newPassword}}；不返回任何历史密码。
     */
    Map<String, Object> resetPassword(Long userId);

    /** 启用 / 停用账号（不能停用自己） */
    AccountView toggleStatus(Long userId, Long operatorUserId);
}
