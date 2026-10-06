package com.gym.shared.audit;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 审计日志实体（对应表 audit_log，见 07 的 V1 迁移脚本）。 */
@Entity
@Table(name = "audit_log")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(length = 512)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected AuditLogEntity() {}

    public AuditLogEntity(String action, String targetType, Long targetId, String detail) {
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detail = detail;
    }

    public Long getId() { return id; }
}
