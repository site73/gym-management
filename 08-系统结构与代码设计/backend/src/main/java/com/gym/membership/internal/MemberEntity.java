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

    /** 预约限制截止时间（SYS-R4：累计爽约达 N 次后限制 restrictDays 天） */
    @Column(name = "penalty_until")
    private LocalDateTime penaltyUntil;

    /** 关联的登录账号（新会员注册时写入；门店代建会员时可为空） */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    protected MemberEntity() {}

    public MemberEntity(Long id, String memberNo, String name, String phone, String status) {
        this.id = id;
        this.memberNo = memberNo;
        this.name = name;
        this.phone = phone;
        this.status = status;
    }

    /** 仅用于本地种子数据显式指定主键 */
    public void setId(Long id) { this.id = id; }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public LocalDateTime getPenaltyUntil() { return penaltyUntil; }
    public void setPenaltyUntil(LocalDateTime penaltyUntil) { this.penaltyUntil = penaltyUntil; }
    public String getMemberNo() { return memberNo; }
    public String getPhone() { return phone; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setName(String name) { this.name = name; }
    public void setPhone(String phone) { this.phone = phone; }
}
