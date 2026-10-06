package com.gym.identity.internal;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 系统用户（对应表 sys_user，见 V1 迁移）。 */
@Entity
@Table(name = "sys_user")
public class SysUserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 128)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 64)
    private String displayName;

    @Column(length = 20)
    private String phone;

    /** active / disabled */
    @Column(nullable = false, length = 16)
    private String status = "active";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected SysUserEntity() {}

    public SysUserEntity(String username, String passwordHash, String displayName) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public String getStatus() { return status; }

    /** 启用/禁用账号（供管理员操作与测试使用） */
    public void setStatus(String status) { this.status = status; }
}
