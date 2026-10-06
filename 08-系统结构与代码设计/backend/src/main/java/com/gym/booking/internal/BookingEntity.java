package com.gym.booking.internal;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 预约实体（对应表 booking，见 07 的 V1 迁移脚本）。 */
@Entity
@Table(name = "booking",
       uniqueConstraints = @UniqueConstraint(name = "uk_booking_member_course",
                                             columnNames = {"member_id", "course_id"}))
public class BookingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    /** booked / checked_in / no_show / cancelled */
    @Column(nullable = false, length = 16)
    private String status = "booked";

    @Column(name = "no_show_prob", precision = 4, scale = 3)
    private BigDecimal noShowProb;

    @Column(name = "booked_at", nullable = false)
    private LocalDateTime bookedAt = LocalDateTime.now();

    @Column(name = "checkin_at")
    private LocalDateTime checkinAt;

    /** scan / front_desk */
    @Column(name = "checkin_channel", length = 16)
    private String checkinChannel;

    @Column(name = "operator_id")
    private Long operatorId;

    protected BookingEntity() {}

    public BookingEntity(Long memberId, Long courseId) {
        this.memberId = memberId;
        this.courseId = courseId;
    }

    /** 状态迁移：只能通过领域方法触发（禁止外部直接 set status）。 */
    public void checkIn(String channel, Long operatorId) {
        if (!"booked".equals(this.status)) {
            throw new IllegalStateException("当前状态不可签到：" + this.status);
        }
        this.status = "checked_in";
        this.checkinAt = LocalDateTime.now();
        this.checkinChannel = channel;
        this.operatorId = operatorId;
    }

    public void markNoShow() {
        if (!"booked".equals(this.status)) {
            throw new IllegalStateException("当前状态不可判爽约：" + this.status);
        }
        this.status = "no_show";
    }

    public void cancel() {
        if (!"booked".equals(this.status)) {
            throw new IllegalStateException("当前状态不可取消：" + this.status);
        }
        this.status = "cancelled";
    }

    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public Long getCourseId() { return courseId; }
    public String getStatus() { return status; }
    public LocalDateTime getCheckinAt() { return checkinAt; }
    public BigDecimal getNoShowProb() { return noShowProb; }
    public void setNoShowProb(BigDecimal p) { this.noShowProb = p; }
}
