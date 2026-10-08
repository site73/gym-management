package com.gym.course.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface CourseRepository extends JpaRepository<CourseEntity, Long> {

    /** 原子占位：只有仍有余位时才 +1（防超卖） */
    @Modifying
    @Query(value = "update course set booked_count = booked_count + 1 " +
                   "where id = :id and booked_count < capacity", nativeQuery = true)
    int occupyOneSeat(@Param("id") Long id);

    /** 释放一个名额 */
    @Modifying
    @Query(value = "update course set booked_count = case when booked_count > 0 then booked_count - 1 else 0 end " +
                   "where id = :id", nativeQuery = true)
    int releaseOneSeat(@Param("id") Long id);

    /** 排课冲突检测（教练或场地在该时段已有课程，按区间重叠判断） */
    @Query(value = "select count(*) from course where status <> 'cancelled' " +
                   "and start_time < :end and end_time > :start " +
                   "and (coach_id = :coachId or room = :room)", nativeQuery = true)
    long countConflict(@Param("coachId") Long coachId, @Param("room") String room,
                       @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 排课冲突检测（排除自身，用于修改课程时避免与自己冲突） */
    @Query(value = "select count(*) from course where status <> 'cancelled' and id <> :id " +
                   "and start_time < :end and end_time > :start " +
                   "and (coach_id = :coachId or room = :room)", nativeQuery = true)
    long countConflictExcluding(@Param("id") Long id, @Param("coachId") Long coachId,
                                @Param("room") String room, @Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end);

    /** 课程编号是否已存在（用于唯一性校验） */
    boolean existsByCode(String code);
}
