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

    public CourseEntity() {}

    public CourseEntity(Long id, String code, String name, Long coachId, String room,
                        LocalDateTime startTime, LocalDateTime endTime, int capacity) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.type = "group";
        this.coachId = coachId;
        this.room = room;
        this.startTime = startTime;
        this.endTime = endTime;
        this.capacity = capacity;
        this.bookedCount = 0;
        this.status = "published";
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getStatus() { return status; }
    public Integer getCapacity() { return capacity; }
    public Integer getBookedCount() { return bookedCount; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public Long getCoachId() { return coachId; }
    public String getRoom() { return room; }

    /* ---- 管理员编辑课程 ---- */
    public void setCode(String code) { this.code = code; }
    public void setName(String name) { this.name = name; }
    public void setType(String type) { this.type = type; }
    public void setCoachId(Long coachId) { this.coachId = coachId; }
    public void setRoom(String room) { this.room = room; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public void setBookedCount(Integer bookedCount) { this.bookedCount = bookedCount; }
    public void setStatus(String status) { this.status = status; }
}
