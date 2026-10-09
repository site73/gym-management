package com.gym.venue.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface VenueBookingRepository extends JpaRepository<VenueBookingEntity, Long> {

    /** 时段冲突检测：同一场地已预约的记录中，是否与 [start, end) 重叠（区间半开） */
    @Query(value = "select count(*) from venue_booking where venue_id = :venueId and status = 'booked' "
                 + "and start_time < :end and end_time > :start", nativeQuery = true)
    long countConflict(@Param("venueId") Long venueId,
                       @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 排除自身后的时段冲突检测（改期用） */
    @Query(value = "select count(*) from venue_booking where venue_id = :venueId and status = 'booked' "
                 + "and id <> :excludeId and start_time < :end and end_time > :start", nativeQuery = true)
    long countConflictExcluding(@Param("venueId") Long venueId, @Param("excludeId") Long excludeId,
                                @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<VenueBookingEntity> findByVenueIdAndStatusOrderByStartTimeAsc(Long venueId, String status);

    List<VenueBookingEntity> findByMemberIdOrderByStartTimeDesc(Long memberId);

    List<VenueBookingEntity> findAllByOrderByStartTimeDesc();

    long countByVenueIdAndStatus(Long venueId, String status);
}
