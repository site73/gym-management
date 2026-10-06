package com.gym.course.internal;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 课程实体（对应表 course，见 07 的 V1 迁移脚本）。 */
@Entity
@Table(name = "course")
public class CourseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 64)
    private String name;

    /** group / pt */
    @Column(nullable = false, length = 16)
    private String type = "group";

    @Column(name = "coach_id")
    private Long coachId;

    @Column(length = 32)
    private String room;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(nullable = false)
    private Integer capacity = 0;

    @Column(name = "booked_count", nullable = false)
    private Integer bookedCount = 0;

    @Column(nullable = false, length = 16)
    private String status = "published";

    public Long getId() { return id; }
    public Integer getCapacity() { return capacity; }
    public Integer getBookedCount() { return bookedCount; }
    public LocalDateTime getStartTime() { return startTime; }
    public Long getCoachId() { return coachId; }
    public String getRoom() { return room; }
}
