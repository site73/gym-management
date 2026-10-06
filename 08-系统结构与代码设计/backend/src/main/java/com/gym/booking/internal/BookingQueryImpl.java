package com.gym.booking.internal;

import com.gym.shared.rule.internal.RuleEngineImpl;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 规则引擎所需的"爽约统计与冲突检测"查询实现。 */
@Component
public class BookingQueryImpl implements RuleEngineImpl.BookingQuery {

    private final BookingRepository bookingRepository;

    public BookingQueryImpl(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Override
    public int noShowCount(Long memberId) {
        return Math.toIntExact(bookingRepository.countByMemberIdAndStatusAndBookedAtAfter(
                memberId, "no_show", LocalDateTime.now().minusMonths(6)));
    }

    @Override
    public Long conflictCourseId(Long memberId, Long courseId) {
        return bookingRepository.findConflictCourseId(memberId, courseId);
    }
}
