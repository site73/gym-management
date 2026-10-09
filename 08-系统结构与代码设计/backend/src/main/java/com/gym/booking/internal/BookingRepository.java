package com.gym.booking.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** 预约仓储（仅本模块使用）。 */
public interface BookingRepository extends JpaRepository<BookingEntity, Long> {

    Optional<BookingEntity> findByMemberIdAndCourseId(Long memberId, Long courseId);

    /** 某课程的全部预约（按预约顺序），用于生成报名名单 */
    java.util.List<BookingEntity> findByCourseIdOrderByIdAsc(Long courseId);

    /** 会员的预约列表（倒序，管理后台用） */
    java.util.List<BookingEntity> findByMemberIdOrderByIdDesc(Long memberId);

    long countByCourseIdAndStatus(Long courseId, String status);

    long countByMemberIdAndStatusAndCheckinAtAfter(Long memberId, String status, java.time.LocalDateTime after);

    java.util.Optional<BookingEntity> findFirstByMemberIdAndStatusOrderByCheckinAtDesc(Long memberId, String status);

    @org.springframework.data.jpa.repository.Query("select b.status, count(b) from BookingEntity b group by b.status")
    java.util.List<Object[]> countGroupByStatus();

    /**
     * 按教练汇总近 N 天已签到的课时数（SYS-R10）。
     * 只读联表 course 取教练，不写对方表。
     */
    @org.springframework.data.jpa.repository.Query(value =
            "select c.coach_id, count(*) from booking b join course c on c.id = b.course_id "
          + "where b.status = 'checked_in' and b.checkin_at >= :from and c.coach_id is not null "
          + "group by c.coach_id", nativeQuery = true)
    java.util.List<Object[]> sumCheckedInTimesByCoach(@Param("from") java.time.LocalDateTime from);

    /** 幂等判重：只针对"有效"状态的预约（取消后可再约同一课程） */
    Optional<BookingEntity> findFirstByMemberIdAndCourseIdAndStatusIn(
            Long memberId, Long courseId, java.util.Collection<String> statuses);

    long countByMemberIdAndStatusAndBookedAtAfter(Long memberId, String status, java.time.LocalDateTime after);

    long countByMemberIdAndBookedAtAfter(Long memberId, java.time.LocalDateTime after);

    /** 会籍过期扫描用：把指定会员的未履约预约置爽约（批量，避免逐条） */
    @Modifying
    @Query("update BookingEntity b set b.status = 'no_show' where b.id = :id and b.status = 'booked'")
    int markNoShow(@Param("id") Long id);

    /** 冲突检测：同一会员在同一时段是否已有未取消的预约 */
    @Query(value = """
            select b.course_id from booking b
            join course c2 on c2.id = :courseId
            join course c on c.id = b.course_id
            where b.member_id = :memberId
              and b.status in ('booked', 'checked_in')
              and c.start_time = c2.start_time
            limit 1
            """, nativeQuery = true)
    Long findConflictCourseId(@Param("memberId") Long memberId, @Param("courseId") Long courseId);
}
