package com.gym.coach.internal;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CoachRepository extends JpaRepository<CoachEntity, Long> {

    /** 教练编号是否已存在（新增时校验唯一） */
    boolean existsByCode(String code);
}
