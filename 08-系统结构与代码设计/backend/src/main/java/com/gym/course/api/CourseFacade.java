package com.gym.course.api;

import java.util.List;

/**
 * 课程模块对外契约。
 *
 * <p>容量与冲突校验在本模块内闭环，其他模块不得直接读写 course 表。
 *
 * <p>课程的增、改、下架属于管理员操作，经 {@code CourseController} 鉴权后调用本契约。
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
    List<CourseView> listCourses();

    /** 单个课程详情 */
    CourseView courseOf(Long courseId);

    /* ---------------- 管理员：课程维护 ---------------- */

    /** 新建课程（校验编号唯一、时间合法、排课不冲突） */
    CourseView createCourse(CourseDraft draft);

    /** 修改课程（容量不得小于已预约人数；时间变更需重新做冲突检测） */
    CourseView updateCourse(Long courseId, CourseDraft draft);

    /** 下架课程（不物理删除，保证历史预约可追溯） */
    void cancelCourse(Long courseId);

    /** 重新上架课程 */
    CourseView publishCourse(Long courseId);
}
