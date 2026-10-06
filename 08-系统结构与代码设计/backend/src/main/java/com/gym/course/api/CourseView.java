package com.gym.course.api;

import java.time.LocalDateTime;

/** 课程对外视图（契约只暴露 DTO，不暴露实体）。 */
public record CourseView(
        Long id,
        String code,
        String name,
        Long coachId,
        String room,
        LocalDateTime startTime,
        LocalDateTime endTime,
        int capacity,
        int bookedCount,
        int remaining,
        String status) {
}
