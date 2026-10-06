package com.gym.membership.internal;

import com.gym.membership.api.MemberView;
import com.gym.membership.api.MembershipFacade;
import com.gym.shared.audit.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 会籍契约实现 —— 本模块内部逻辑，其他模块不可依赖本类。
 */
@Service
public class MembershipFacadeImpl implements MembershipFacade {

    private final MemberRepository memberRepository;
    private final MembershipRepository membershipRepository;
    private final FollowTaskRepository followTaskRepository;
    private final AuditLogger auditLogger;

    public MembershipFacadeImpl(MemberRepository memberRepository,
                                MembershipRepository membershipRepository,
                                FollowTaskRepository followTaskRepository,
                                AuditLogger auditLogger) {
        this.memberRepository = memberRepository;
        this.membershipRepository = membershipRepository;
        this.followTaskRepository = followTaskRepository;
        this.auditLogger = auditLogger;
    }

    @Override
    public boolean isEffective(Long memberId) {
        return "active".equals(statusOf(memberId));
    }

    @Override
    public String statusOf(Long memberId) {
        return memberRepository.findById(memberId)
                .map(MemberEntity::getStatus)
                .orElse("expired");
    }

    @Override
    public int packageRemaining(Long memberId) {
        return membershipRepository.findActiveByMemberId(memberId)
                .map(m -> m.getRemainingTimes() == null ? 0 : m.getRemainingTimes())
                .orElse(0);
    }

    @Override
    @Transactional
    public int deductPackage(Long memberId, int times) {
        var membership = membershipRepository.findActiveByMemberId(memberId)
                .orElseThrow(() -> new IllegalStateException("无可核销的课包"));
        int remaining = Math.max(0, (membership.getRemainingTimes() == null ? 0 : membership.getRemainingTimes()) - times);
        membership.setRemainingTimes(remaining);
        membershipRepository.save(membership);
        auditLogger.log("membership.deductPackage", "membership", membership.getId(),
                "剩余 " + remaining + " 次");
        return remaining;
    }

    @Override
    @Transactional
    public Long createFollowTask(Long memberId, String taskType, String ruleCode) {
        var task = new FollowTaskEntity(memberId, taskType, ruleCode);
        var saved = followTaskRepository.save(task);
        auditLogger.log("followTask.create", "follow_task", saved.getId(), taskType);
        return saved.getId();
    }

    @Override
    @Transactional
    public void activateMembership(Long memberId, String bizType, int packageTimes) {
        var member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("会员不存在：" + memberId));
        member.setStatus("active");
        memberRepository.save(member);

        if ("pt_package".equals(bizType)) {
            var membership = membershipRepository.findActiveByMemberId(memberId)
                    .orElseGet(() -> new MembershipEntity(memberId, "pt_package"));
            membership.setRemainingTimes((membership.getRemainingTimes() == null ? 0 : membership.getRemainingTimes()) + packageTimes);
            membershipRepository.save(membership);
        }
        auditLogger.log("membership.activate", "member", memberId, bizType);
    }

    @Override
    public List<MemberView> listMembers() {
        return memberRepository.findAll().stream()
                .map(m -> new MemberView(m.getId(), null, m.getName(), null, m.getStatus(), m.getRiskLevel()))
                .toList();
    }

    /** SYS-R6：扫描 D 天内到期的有效会籍，返回需提醒的会员 */
    @Override
    public List<String> remindExpiring(int daysBefore) {
        LocalDate today = LocalDate.now();
        var expiring = membershipRepository.findByStatusAndEndDateBetween(
                "active", today, today.plusDays(daysBefore));
        List<String> names = new ArrayList<>();
        for (var ms : expiring) {
            memberRepository.findById(ms.getMemberId()).ifPresent(m -> names.add(m.getName()));
        }
        auditLogger.log("job.renewRemind", "membership", null,
                "days=" + daysBefore + ", count=" + names.size());
        return names;
    }
}
