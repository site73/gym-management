package com.gym.coach.internal;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 教练档案（对应表 coach，见 07 的 V11 迁移脚本）。 */
@Entity
@Table(name = "coach")
public class CoachEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 20)
    private String phone;

    /** 擅长项目，用于排课与展示 */
    @Column(length = 64)
    private String specialty;

    /** active 在职 / leave 休假 */
    @Column(nullable = false, length = 16)
    private String status = "active";

    /** 关联的登录账号（coach.user_id） */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    protected CoachEntity() {}

    public CoachEntity(Long id, String code, String name, String phone, String specialty, String status) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.phone = phone;
        this.specialty = specialty;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getSpecialty() { return specialty; }
    public String getStatus() { return status; }
    public Long getUserId() { return userId; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCode(String code) { this.code = code; }
    public void setName(String name) { this.name = name; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }
    public void setStatus(String status) { this.status = status; }
    public void setUserId(Long userId) { this.userId = userId; }
}
