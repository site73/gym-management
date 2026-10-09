package com.gym.identity.api;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 账号管理入口（超级管理员专属）。
 *
 * <p>权限：全部接口都要求 {@code super_admin} 角色，其他角色（含普通管理员、店长）一律 403。
 *
 * <p><b>密码策略说明</b>：密码以 BCrypt 哈希存储，单向不可逆，因此**没有"查看明文密码"接口**。
 * 需要掌握某账号凭据时使用「重置密码」：重置为默认密码并把新密码返回给超级管理员。
 * 这样既满足"管理员知道账号能怎么登"的实际需要，又避免全部账号处于明文可读的高风险状态。
 */
@RestController
@RequestMapping("/api/admin/accounts")
public class AccountAdminController {

    private final AccountAdminFacade accountAdminFacade;

    public AccountAdminController(AccountAdminFacade accountAdminFacade) {
        this.accountAdminFacade = accountAdminFacade;
    }

    /** 账号总览（含角色、绑定会员/教练、状态、密码重置时间） */
    @GetMapping
    public List<AccountView> list() {
        AuthContext.requireSuperAdmin();
        return accountAdminFacade.listAccounts();
    }

    /** 单个账号 */
    @GetMapping("/{id}")
    public AccountView detail(@PathVariable Long id) {
        AuthContext.requireSuperAdmin();
        return accountAdminFacade.accountOf(id);
    }

    /** 重置密码为默认值（返回新密码；不返回任何历史密码） */
    @PostMapping("/{id}/reset-password")
    public Map<String, Object> resetPassword(@PathVariable Long id) {
        AuthContext.requireSuperAdmin();
        return accountAdminFacade.resetPassword(id);
    }

    /** 启用 / 停用账号 */
    @PostMapping("/{id}/toggle-status")
    public AccountView toggleStatus(@PathVariable Long id) {
        var session = AuthContext.requireSuperAdmin();
        return accountAdminFacade.toggleStatus(id, session.userId());
    }
}
