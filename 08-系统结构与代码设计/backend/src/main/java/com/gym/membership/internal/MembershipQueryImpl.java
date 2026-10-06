package com.gym.membership.internal;

import com.gym.shared.rule.internal.RuleEngineImpl;
import org.springframework.stereotype.Component;

/** 规则引擎所需的"会籍状态"只读查询实现（跨模块只读，不暴露实体）。 */
@Component
public class MembershipQueryImpl implements RuleEngineImpl.MembershipQuery {

    private final MemberRepository memberRepository;

    public MembershipQueryImpl(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    public String statusOf(Long memberId) {
        return memberRepository.findById(memberId).map(MemberEntity::getStatus).orElse("expired");
    }

    @Override
    public int penaltyDaysRemaining(Long memberId) {
        return memberRepository.findById(memberId)
                .map(MemberEntity::getPenaltyUntil)
                .filter(until -> until != null && until.isAfter(java.time.LocalDateTime.now()))
                .map(until -> (int) Math.ceil(java.time.Duration
                        .between(java.time.LocalDateTime.now(), until).toMinutes() / 1440.0))
                .orElse(0);
    }
}
