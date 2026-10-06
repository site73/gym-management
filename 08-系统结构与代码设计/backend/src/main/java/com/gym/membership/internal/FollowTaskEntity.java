package com.gym.membership.internal;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 跟进任务实体（对应表 follow_task；承载 SYS-R7 高风险跟进与 SYS-R9 首月跟进）。 */
@Entity
@Table(name = "follow_task")
public class FollowTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** new_member_30d / risk_high / manual */
    @Column(name = "task_type", nullable = false, length = 32)
    private String taskType;

    @Column(name = "rule_code", length = 32)
    private String ruleCode;

    @Column(name = "assignee_id")
    private Long assigneeId;

    @Column(nullable = false, length = 16)
    private String status = "pending";

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    protected FollowTaskEntity() {}

    public FollowTaskEntity(Long memberId, String taskType, String ruleCode) {
        this.memberId = memberId;
        this.taskType = taskType;
        this.ruleCode = ruleCode;
    }

    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public String getTaskType() { return taskType; }
    public String getRuleCode() { return ruleCode; }
}
