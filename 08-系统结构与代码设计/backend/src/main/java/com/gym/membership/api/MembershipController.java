package com.gym.membership.api;

import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会员与会籍查询入口（后台管理页用）。
 * 只依赖模块对外契约 {@link MembershipFacade}。
 */
@RestController
@RequestMapping("/api")
public class MembershipController {

    private final MembershipFacade membershipFacade;

    public MembershipController(MembershipFacade membershipFacade) {
        this.membershipFacade = membershipFacade;
    }

    /** 会员列表 */
    @GetMapping("/members")
    public List<MemberView> members() {
        return membershipFacade.listMembers();
    }

    /** 单会员会籍状态（约课校验用） */
    @GetMapping("/members/{id}/status")
    public java.util.Map<String, Object> status(@PathVariable Long id) {
        return java.util.Map.of(
                "memberId", id,
                "status", membershipFacade.statusOf(id),
                "effective", membershipFacade.isEffective(id),
                "packageRemaining", membershipFacade.packageRemaining(id));
    }

    /** 会籍到期提醒扫描（SYS-R6，默认提前 7 天） */
    @PostMapping("/jobs/renew-remind")
    public java.util.Map<String, Object> renewRemind(@RequestParam(defaultValue = "7") int days) {
        List<String> reminded = membershipFacade.remindExpiring(days);
        return java.util.Map.of("daysBefore", days, "count", reminded.size(), "members", reminded);
    }
}
