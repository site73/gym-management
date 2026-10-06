package com.gym.membership.internal;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 会员实体（对应表 member，见 07 的 V1 迁移脚本）。 */
@Entity
@Table(name = "member")
public class MemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_no", nullable = false, unique = true, length = 32)
    private String memberNo;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 20)
    private String phone;

    /** potential / active / frozen / expired / lost */
    @Column(nullable = false, length = 16)
    private String status = "potential";

    @Column(name = "risk_level", length = 16)
    private String riskLevel;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
}
