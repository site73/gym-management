package com.gym.membership.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MembershipRepository extends JpaRepository<MembershipEntity, Long> {

    /** 会员当前有效的会籍/课包（按结束日期倒序取最近一条） */
    Optional<MembershipEntity> findFirstByMemberIdAndStatusOrderByEndDateDesc(Long memberId, String status);

    default Optional<MembershipEntity> findActiveByMemberId(Long memberId) {
        return findFirstByMemberIdAndStatusOrderByEndDateDesc(memberId, "active");
    }

    /** 到期提醒扫描（SYS-R6）：有效期内在指定日期区间到期的会籍 */
    java.util.List<MembershipEntity> findByStatusAndEndDateBetween(
            String status, java.time.LocalDate from, java.time.LocalDate to);
}
