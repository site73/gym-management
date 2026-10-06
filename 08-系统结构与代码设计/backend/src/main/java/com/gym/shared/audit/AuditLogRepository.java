package com.gym.shared.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

    /** 最近 50 条审计记录（倒序） */
    List<AuditLogEntity> findTop50ByOrderByIdDesc();
}
