package com.gym.course.internal;

import com.gym.shared.rule.internal.RuleEngineImpl;
import org.springframework.stereotype.Component;

/** 规则引擎所需的"课程余位"只读查询实现。 */
@Component
public class CourseQueryImpl implements RuleEngineImpl.CourseQuery {

    private final CourseRepository courseRepository;

    public CourseQueryImpl(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Override
    public int remainingSeats(Long courseId) {
        return courseRepository.findById(courseId)
                .map(c -> Math.max(0, c.getCapacity() - c.getBookedCount()))
                .orElse(0);
    }
}
