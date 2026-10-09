package com.gym.venue.internal;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 场馆 / 区域（对应表 venue，见 07 的 V11 迁移脚本）。 */
@Entity
@Table(name = "venue")
public class VenueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 64)
    private String name;

    /** private 私有场馆 / public 公共区域 */
    @Column(nullable = false, length = 16)
    private String type = "private";

    @Column(nullable = false)
    private Integer capacity = 1;

    @Column(length = 64)
    private String location;

    /** 按时使用费（0 表示免费） */
    @Column(name = "hourly_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal hourlyFee = BigDecimal.ZERO;

    /** available 可用 / maintenance 维护中 */
    @Column(nullable = false, length = 16)
    private String status = "available";

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    protected VenueEntity() {}

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getType() { return type; }
    public Integer getCapacity() { return capacity; }
    public String getLocation() { return location; }
    public BigDecimal getHourlyFee() { return hourlyFee; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCode(String code) { this.code = code; }
    public void setName(String name) { this.name = name; }
    public void setType(String type) { this.type = type; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public void setLocation(String location) { this.location = location; }
    public void setHourlyFee(BigDecimal hourlyFee) { this.hourlyFee = hourlyFee; }
    public void setStatus(String status) { this.status = status; }
}
