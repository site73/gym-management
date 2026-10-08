package com.gym.course.api;

import java.time.LocalDateTime;

/**
 * 课程编辑入参（管理员新建/修改课程时使用）。
 *
 * @param code      课程编号（新建时必填且唯一，建后不可改）
 * @param name      课程名称
 * @param type      group / pt，为空默认 group
 * @param coachId   教练 ID
 * @param room      场地
 * @param startTime 开始时间
 * @param endTime   结束时间（必须晚于开始时间）
 * @param capacity  容量（必须 ≥1，且不小于已预约人数）
 */
public record CourseDraft(
        String code,
        String name,
        String type,
        Long coachId,
        String room,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer capacity) {
}
