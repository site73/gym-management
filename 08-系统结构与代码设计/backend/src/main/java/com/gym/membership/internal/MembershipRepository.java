package com.gym.membership.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MembershipRepository extends JpaRepository<MembershipEntity, Long> {

    /** 会员当前有效的会籍/课包（按结束日期倒序取最近一条） */
    Optional<MembershipEntity> findFirstByMemberIdAndStatusOrderByEndDateDesc(Long memberId, String status);

    default Optional<MembershipEntity> findActiveByMemberId(Long memberId) {
        return findFirstByMemberIdAndStatusOrderByEndDateDesc(memberId, "active");
    }
}
