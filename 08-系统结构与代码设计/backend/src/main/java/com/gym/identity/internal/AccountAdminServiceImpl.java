package com.gym.identity.internal;

import com.gym.coach.api.CoachFacade;
import com.gym.identity.api.AccountAdminFacade;
import com.gym.identity.api.AccountView;
import com.gym.membership.api.MembershipFacade;
import com.gym.shared.audit.AuditLogger;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 账号管理实现（超级管理员专属）。
 *
 * <p>工程要点：账号总览用 **4 次批量查询**装配（账号、角色、会员绑定、教练绑定），
 * 不做逐账号查询，避免账号数量增长后的 N+1 问题。
 */
@Service
public class AccountAdminServiceImpl implements AccountAdminFacade {

    /** 重置密码时使用的默认密码（与演示账号一致） */
    public static final String DEFAULT_PASSWORD = "123456";

    private final SysUserRepository userRepository;
    private final MembershipFacade membershipFacade;
    private final CoachFacade coachFacade;
    private final AuditLogger auditLogger;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    public AccountAdminServiceImpl(SysUserRepository userRepository,
                                   MembershipFacade membershipFacade,
                                   CoachFacade coachFacade,
                                   AuditLogger auditLogger) {
        this.userRepository = userRepository;
        this.membershipFacade = membershipFacade;
        this.coachFacade = coachFacade;
        this.auditLogger = auditLogger;
    }

    @Override
    public List<AccountView> listAccounts() {
        // 批量取回：角色、会员绑定、教练绑定
        Map<Long, List<String>> roleMap = new HashMap<>();
        for (Object[] r : userRepository.allUserRoles()) {
            roleMap.computeIfAbsent(num(r[0]), k -> new ArrayList<>()).add((String) r[1]);
        }
        Map<Long, Long> memberByUser = new HashMap<>();
        for (Object[] r : userRepository.allMemberBindings()) {
            memberByUser.put(num(r[1]), num(r[0]));
        }
        Map<Long, Long> coachByUser = new HashMap<>();
        for (Object[] r : userRepository.allCoachBindings()) {
            coachByUser.put(num(r[1]), num(r[0]));
        }
        Map<Long, String> memberNames = membershipFacade.listMembers().stream()
                .collect(Collectors.toMap(m -> m.id(), m -> m.name(), (a, b) -> a));
        Map<Long, String> coachNames = coachFacade.listCoaches().stream()
                .collect(Collectors.toMap(c -> c.id(), c -> c.name(), (a, b) -> a));

        return userRepository.listAccounts().stream().map(row -> {
            Long id = num(row[0]);
            List<String> roles = roleMap.getOrDefault(id, List.of());
            Long memberId = memberByUser.get(id);
            Long coachId = coachByUser.get(id);
            return new AccountView(
                    id, (String) row[1], (String) row[2],
                    roles, AccountView.roleCnOf(roles),
                    memberId, memberId == null ? null : memberNames.get(memberId),
                    coachId, coachId == null ? null : coachNames.get(coachId),
                    (String) row[3], AccountView.statusCnOf((String) row[3]),
                    roles.contains("admin"), roles.contains("super_admin"),
                    ldt(row[4]), ldt(row[5]), AccountView.passwordHintOf(ldt(row[5])));
        }).toList();
    }

    @Override
    public AccountView accountOf(Long userId) {
        return listAccounts().stream()
                .filter(a -> a.id().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("账号不存在：" + userId));
    }

    @Override
    @Transactional
    public Map<String, Object> resetPassword(Long userId) {
        var view = accountOf(userId);
        userRepository.resetPassword(userId, encoder.encode(DEFAULT_PASSWORD));
        auditLogger.log("account.resetPassword", "sys_user", userId,
                "重置账号 " + view.username() + " 的密码");
        return Map.of(
                "ok", true,
                "userId", userId,
                "username", view.username(),
                "displayName", view.displayName(),
                "newPassword", DEFAULT_PASSWORD,
                "message", "已将 " + view.username() + " 的密码重置为默认密码，请提醒本人尽快修改");
    }

    @Override
    @Transactional
    public AccountView toggleStatus(Long userId, Long operatorUserId) {
        if (userId.equals(operatorUserId)) {
            throw new IllegalStateException("不能停用当前登录的账号");
        }
        var view = accountOf(userId);
        if (view.superAdmin() && "active".equals(view.status())) {
            throw new IllegalStateException("不能停用超级管理员账号");
        }
        String next = "active".equals(view.status()) ? "disabled" : "active";
        userRepository.updateStatus(userId, next);
        auditLogger.log("account.toggleStatus", "sys_user", userId,
                "账号 " + view.username() + " → " + ("active".equals(next) ? "启用" : "停用"));
        return accountOf(userId);
    }

    /* ---------- 工具 ---------- */

    private static Long num(Object o) {
        return o == null ? null : ((Number) o).longValue();
    }

    private static LocalDateTime ldt(Object o) {
        if (o == null) return null;
        if (o instanceof LocalDateTime l) return l;
        if (o instanceof Timestamp t) return t.toLocalDateTime();
        return null;
    }
}
