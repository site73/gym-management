package com.gym.booking.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** 预约仓储（仅本模块使用）。 */
public interface BookingRepository extends JpaRepository<BookingEntity, Long> {

    Optional<BookingEntity> findByMemberIdAndCourseId(Long memberId, Long courseId);

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
