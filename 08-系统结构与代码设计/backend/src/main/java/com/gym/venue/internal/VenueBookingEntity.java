package com.gym.venue.internal;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 场地预约（对应表 venue_booking，见 07 的 V11 迁移脚本）。 */
@Entity
@Table(name = "venue_booking")
public class VenueBookingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "venue_id", nullable = false)
    private Long venueId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    /** booked 已预约 / cancelled 已取消 / finished 已结束 */
    @Column(nullable = false, length = 16)
    private String status = "booked";

    /** 发起人：会员自助为空，门店代约为店员 ID */
    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    protected VenueBookingEntity() {}

    public VenueBookingEntity(Long venueId, Long memberId,
                              LocalDateTime startTime, LocalDateTime endTime, Long createdBy) {
        this.venueId = venueId;
        this.memberId = memberId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.createdBy = createdBy;
        this.status = "booked";
    }

    public Long getId() { return id; }
    public Long getVenueId() { return venueId; }
    public Long getMemberId() { return memberId; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public String getStatus() { return status; }
    public Long getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setStatus(String status) { this.status = status; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
}
