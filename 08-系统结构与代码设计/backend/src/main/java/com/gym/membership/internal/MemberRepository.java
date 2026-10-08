package com.gym.membership.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MemberRepository extends JpaRepository<MemberEntity, Long> {

    /** 当前最大会员 ID，用于生成会员编号 M001 / M002 … */
    @Query(value = "select coalesce(max(id), 0) from member", nativeQuery = true)
    long maxId();
}
