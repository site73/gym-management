package com.gym.course.internal;

import com.gym.course.api.CourseDraft;
import com.gym.course.api.CourseFacade;
import com.gym.course.api.CourseView;
import com.gym.shared.audit.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 课程契约实现。容量以数据库条件更新保证并发安全（booked_count &lt; capacity 才允许占用）。
 *
 * <p>课程维护（增/改/下架）在落库前统一做四类校验：
 * 编号唯一、时间合法、容量合理、排课不冲突（SYS-R3）。
 */
@Service
public class CourseFacadeImpl implements CourseFacade {

    private final CourseRepository courseRepository;
    private final AuditLogger auditLogger;

    public CourseFacadeImpl(CourseRepository courseRepository, AuditLogger auditLogger) {
        this.courseRepository = courseRepository;
        this.auditLogger = auditLogger;
    }

    @Override
    public int remainingSeats(Long courseId) {
        return Math.max(0, load(courseId).getCapacity() - load(courseId).getBookedCount());
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
        return load(courseId).getStartTime();
    }

    @Override
    public boolean willConflict(Long coachId, String room, LocalDateTime start, LocalDateTime end) {
        return courseRepository.countConflict(coachId, room, start, end) > 0;
    }

    @Override
    public List<CourseView> listCourses() {
        return courseRepository.findAll().stream().map(this::toView).toList();
    }

    @Override
    public CourseView courseOf(Long courseId) {
        return toView(load(courseId));
    }

    /* ==================== 管理员：课程维护 ==================== */

    @Override
    @Transactional
    public CourseView createCourse(CourseDraft draft) {
        requireDraft(draft);
        if (draft.code() == null || draft.code().isBlank()) {
            throw new IllegalArgumentException("课程编号不能为空");
        }
        String code = draft.code().trim();
        if (courseRepository.existsByCode(code)) {
            throw new IllegalArgumentException("课程编号已存在：" + code);
        }
        validateTime(draft, null);

        var course = new CourseEntity();
        course.setCode(code);
        course.setName(draft.name().trim());
        course.setType(normalizeType(draft.type()));
        course.setCoachId(draft.coachId());
        course.setRoom(blankToNull(draft.room()));
        course.setStartTime(draft.startTime());
        course.setEndTime(draft.endTime());
        course.setCapacity(draft.capacity());
        course.setStatus("published");

        var saved = courseRepository.save(course);
        auditLogger.log("course.create", "course", saved.getId(),
                saved.getCode() + " " + saved.getName() + " 容量" + saved.getCapacity());
        return toView(saved);
    }

    @Override
    @Transactional
    public CourseView updateCourse(Long courseId, CourseDraft draft) {
        requireDraft(draft);
        var course = load(courseId);

        validateTime(draft, courseId);

        int booked = course.getBookedCount() == null ? 0 : course.getBookedCount();
        if (draft.capacity() < booked) {
            throw new IllegalArgumentException(
                    "容量不能小于已预约人数（已约 " + booked + " 人）");
        }

        course.setName(draft.name().trim());
        course.setType(normalizeType(draft.type()));
        course.setCoachId(draft.coachId());
        course.setRoom(blankToNull(draft.room()));
        course.setStartTime(draft.startTime());
        course.setEndTime(draft.endTime());
        course.setCapacity(draft.capacity());

        var saved = courseRepository.save(course);
        auditLogger.log("course.update", "course", saved.getId(),
                saved.getCode() + " 容量" + saved.getCapacity());
        return toView(saved);
    }

    @Override
    @Transactional
    public void cancelCourse(Long courseId) {
        var course = load(courseId);
        course.setStatus("cancelled");
        courseRepository.save(course);
        auditLogger.log("course.cancel", "course", courseId, "下架：" + course.getCode());
    }

    @Override
    @Transactional
    public CourseView publishCourse(Long courseId) {
        var course = load(courseId);
        if (!course.getEndTime().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("课程已结束，不能重新上架");
        }
        course.setStatus("published");
        var saved = courseRepository.save(course);
        auditLogger.log("course.publish", "course", courseId, "重新上架：" + course.getCode());
        return toView(saved);
    }

    /* ==================== 校验与工具 ==================== */

    private CourseEntity load(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("课程不存在：" + courseId));
    }

    /** 入参完整性校验（编号在创建时必填，修改时不可改） */
    private void requireDraft(CourseDraft draft) {
        if (draft == null) throw new IllegalArgumentException("缺少课程信息");
        if (draft.name() == null || draft.name().isBlank()) {
            throw new IllegalArgumentException("课程名称不能为空");
        }
        if (draft.startTime() == null || draft.endTime() == null) {
            throw new IllegalArgumentException("开始与结束时间不能为空");
        }
        if (!draft.endTime().isAfter(draft.startTime())) {
            throw new IllegalArgumentException("结束时间必须晚于开始时间");
        }
        if (draft.capacity() == null || draft.capacity() < 1) {
            throw new IllegalArgumentException("容量必须至少为 1");
        }
    }

    /** 时间合法性与排课冲突校验（SYS-R3）；excludeId 非空时排除自身 */
    private void validateTime(CourseDraft draft, Long excludeId) {
        if (draft.startTime().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("开始时间不能早于当前时间");
        }
        boolean conflict = excludeId == null
                ? courseRepository.countConflict(draft.coachId(), draft.room(),
                        draft.startTime(), draft.endTime()) > 0
                : courseRepository.countConflictExcluding(excludeId, draft.coachId(), draft.room(),
                        draft.startTime(), draft.endTime()) > 0;
        if (conflict) {
            throw new IllegalArgumentException("该教练或场地在此时间段已有排课（SYS-R3）");
        }
    }

    private String normalizeType(String type) {
        return (type == null || type.isBlank()) ? "group" : type.trim();
    }

    private String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    private CourseView toView(CourseEntity c) {
        int capacity = c.getCapacity() == null ? 0 : c.getCapacity();
        int booked = c.getBookedCount() == null ? 0 : c.getBookedCount();
        return new CourseView(c.getId(), c.getCode(), c.getName(), c.getType(), c.getCoachId(),
                c.getRoom(), c.getStartTime(), c.getEndTime(), capacity, booked,
                Math.max(0, capacity - booked), c.getStatus());
    }
}
