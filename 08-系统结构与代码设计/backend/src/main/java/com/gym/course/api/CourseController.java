package com.gym.course.api;

import com.gym.booking.api.BookingDetailView;
import com.gym.booking.api.BookingFacade;
import com.gym.identity.api.AuthContext;
import com.gym.identity.api.AuthException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 课程入口。
 *
 * <p>查询对已登录用户开放（会员选课、后台查看）；
 * <b>新建（排课）/ 修改 / 下架 / 上架属于门店后台操作</b>，由服务端强制校验角色（会员返回 403）。
 */
@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseFacade courseFacade;
    private final BookingFacade bookingFacade;

    public CourseController(CourseFacade courseFacade, BookingFacade bookingFacade) {
        this.courseFacade = courseFacade;
        this.bookingFacade = bookingFacade;
    }

    /** 课程列表（登录即可） */
    @GetMapping
    public List<CourseView> list() {
        return courseFacade.listCourses();
    }

    /** 课程详情（登录即可） */
    @GetMapping("/{id}")
    public CourseView detail(@PathVariable Long id) {
        return courseFacade.courseOf(id);
    }

    /**
     * 某课程的报名名单（含会员姓名）——门店后台与任课教练可查。
     *
     * <p>课程满员时（如 15/15），可据此查到全部报名会员。
     */
    @GetMapping("/{id}/bookings")
    public List<BookingDetailView> roster(@PathVariable Long id) {
        var s = AuthContext.current();
        boolean allowed = s.isStaff();
        if (!allowed && s.isCoach() && s.coachId() != null) {
            allowed = s.coachId().equals(courseFacade.courseOf(id).coachId());
        }
        if (!allowed) {
            throw AuthException.forbidden("仅门店后台或该课教练可查看报名名单");
        }
        return bookingFacade.listBookingsByCourse(id);
    }

    /** 新建课程（管理员） */
    @PostMapping
    public ResponseEntity<CourseView> create(@RequestBody CourseDraft draft) {
        AuthContext.requireStaff();
        return ResponseEntity.ok(courseFacade.createCourse(draft));
    }

    /** 修改课程（管理员） */
    @PutMapping("/{id}")
    public CourseView update(@PathVariable Long id, @RequestBody CourseDraft draft) {
        AuthContext.requireStaff();
        return courseFacade.updateCourse(id, draft);
    }

    /**
     * 下架课程（管理员）。
     *
     * <p>不物理删除：历史预约仍需可追溯。若该课程仍有待上课的预约（status=booked），
     * 返回 409 提示先处理，避免会员到了现场发现课没了。
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable Long id) {
        AuthContext.requireStaff();
        long active = bookingFacade.countActiveBookingsOfCourse(id);
        if (active > 0) {
            return ResponseEntity.status(409).body(Map.of(
                    "ok", false, "code", "COURSE_HAS_BOOKINGS",
                    "reason", "该课程还有 " + active + " 条待上课预约，请先取消或改期后再下架"));
        }
        courseFacade.cancelCourse(id);
        return ResponseEntity.ok(Map.of("ok", true, "message", "课程已下架"));
    }

    /** 重新上架课程（管理员） */
    @PostMapping("/{id}/publish")
    public CourseView publish(@PathVariable Long id) {
        AuthContext.requireStaff();
        return courseFacade.publishCourse(id);
    }
}
