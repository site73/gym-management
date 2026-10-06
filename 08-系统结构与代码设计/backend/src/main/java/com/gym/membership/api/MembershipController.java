package com.gym.membership.api;

import com.gym.identity.api.AuthContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 会员与会籍入口。
 *
 * <p>会员账号只能读取本人信息；会员列表与定时任务仅门店后台可用。
 */
@RestController
@RequestMapping("/api")
public class MembershipController {

    private final MembershipFacade membershipFacade;

    public MembershipController(MembershipFacade membershipFacade) {
        this.membershipFacade = membershipFacade;
    }

    /** 会员列表（仅门店后台） */
    @GetMapping("/members")
    public List<MemberView> members() {
        AuthContext.requireStaff();
        return membershipFacade.listMembers();
    }

    /** 会员详情（本人或门店） */
    @GetMapping("/members/{id}")
    public MemberView member(@PathVariable Long id) {
        AuthContext.assertSelfOrStaff(id);
        return membershipFacade.memberOf(id);
    }

    /** 会籍状态（本人或门店） */
    @GetMapping("/members/{id}/status")
    public Map<String, Object> status(@PathVariable Long id) {
        AuthContext.assertSelfOrStaff(id);
        return Map.of(
                "memberId", id,
                "status", membershipFacade.statusOf(id),
                "effective", membershipFacade.isEffective(id),
                "packageRemaining", membershipFacade.packageRemaining(id),
                "penaltyDaysRemaining", membershipFacade.penaltyDaysRemaining(id));
    }

    /** 会籍到期提醒扫描（SYS-R6，仅门店后台） */
    @PostMapping("/jobs/renew-remind")
    public Map<String, Object> renewRemind(@RequestParam(defaultValue = "7") int days) {
        AuthContext.requireStaff();
        List<String> reminded = membershipFacade.remindExpiring(days);
        return Map.of("daysBefore", days, "count", reminded.size(), "members", reminded);
    }
}
