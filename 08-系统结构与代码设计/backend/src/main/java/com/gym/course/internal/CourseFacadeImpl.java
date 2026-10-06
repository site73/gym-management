package com.gym.course.internal;

import com.gym.course.api.CourseFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 课程契约实现。容量以数据库条件更新保证并发安全（booked_count < capacity 才允许占用）。
 */
@Service
public class CourseFacadeImpl implements CourseFacade {

    private final CourseRepository courseRepository;

    public CourseFacadeImpl(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Override
    public int remainingSeats(Long courseId) {
        var course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("课程不存在：" + courseId));
        return Math.max(0, course.getCapacity() - course.getBookedCount());
    }

    @Override
    @Transactional
    public boolean occupySeat(Long courseId) {
        // 条件更新：只有仍有余位时才 +1，避免超卖
        return courseRepository.occupyOneSeat(courseId) == 1;
    }

    @Override
    @Transactional
    public void releaseSeat(Long courseId) {
        courseRepository.releaseOneSeat(courseId);
    }

    @Override
    public LocalDateTime startTimeOf(Long courseId) {
        return courseRepository.findById(courseId).orElseThrow().getStartTime();
    }

    @Override
    public boolean willConflict(Long coachId, String room, LocalDateTime start, LocalDateTime end) {
        return courseRepository.countConflict(coachId, room, start, end) > 0;
    }
}
