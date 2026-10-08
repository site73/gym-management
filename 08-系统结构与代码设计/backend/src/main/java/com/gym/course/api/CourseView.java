package com.gym.course.api;

import java.time.LocalDateTime;

/** 课程对外视图（契约只暴露 DTO，不暴露实体）。 */
public record CourseView(
        Long id,
        String code,
        String name,
        /** group（团课）/ pt（私教） */
        String type,
        Long coachId,
        String room,
        LocalDateTime startTime,
        LocalDateTime endTime,
        int capacity,
        int bookedCount,
        int remaining,
        String status) {

    /** 状态中文名，供前端与后台展示 */
    public String statusCn() {
        return switch (status == null ? "" : status) {
            case "published" -> "已发布";
            case "full" -> "已满员";
            case "ongoing" -> "进行中";
            case "finished" -> "已结束";
            case "cancelled" -> "已下架";
            default -> status;
        };
    }

    /** 类型中文名 */
    public String typeCn() {
        return "pt".equals(type) ? "私教" : "团课";
    }
}
