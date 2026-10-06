package com.gym.course.api;

/**
 * 课程模块对外契约。
 * 容量与冲突校验在本模块内闭环，其他模块不得直接读写 course 表。
 */
public interface CourseFacade {

    /** 剩余名额 */
    int remainingSeats(Long courseId);

    /** 占用一个名额（原子，容量不足返回 false） */
    boolean occupySeat(Long courseId);

    /** 释放一个名额 */
    void releaseSeat(Long courseId);

    /** 课程开始时间（用于签到窗口与冲突判断） */
    java.time.LocalDateTime startTimeOf(Long courseId);

    /** 排课冲突检测（SYS-R3） */
    boolean willConflict(Long coachId, String room, java.time.LocalDateTime start, java.time.LocalDateTime end);

    /** 课程列表（含剩余名额），供小程序端与后台展示 */
    java.util.List<CourseView> listCourses();
}
