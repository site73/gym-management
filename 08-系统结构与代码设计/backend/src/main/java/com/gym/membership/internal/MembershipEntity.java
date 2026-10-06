package com.gym.membership.internal;

import jakarta.persistence.*;
import java.time.LocalDate;

/** 会籍合同实体（对应表 membership）。 */
@Entity
@Table(name = "membership")
public class MembershipEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** month / quarter / year / pt_package / times */
    @Column(nullable = false, length = 16)
    private String type;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false, length = 16)
    private String status = "active";

    @Column(name = "remaining_times")
    private Integer remainingTimes;

    protected MembershipEntity() {}

    public MembershipEntity(Long memberId, String type) {
        this.memberId = memberId;
        this.type = type;
        this.startDate = LocalDate.now();
        this.endDate = LocalDate.now().plusYears(1);
    }

    public Long getId() { return id; }
    public Integer getRemainingTimes() { return remainingTimes; }
    public void setRemainingTimes(Integer t) { this.remainingTimes = t; }
}
