package com.gym.booking.api;

import com.gym.booking.application.BookingAppService;
import com.gym.course.api.CourseFacade;
import com.gym.identity.api.AuthContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 选课 / 退课入口 —— 面向<b>所有登录用户</b>的"自愿"操作。
 *
 * <p>与 {@link BookingController} 的分工：
 * <ul>
 *   <li>本控制器只表达"会员本人自愿报名 / 自愿退出"。会员账号固定作用于本人，
 *       即便伪造 memberId 也会被服务端改写；</li>
 *   <li>{@link BookingController} 侧重门店后台的预约管理（代约、代签、判爽约）。</li>
 * </ul>
 *
 * <h2>退课规则（本次新增）</h2>
 * <ol>
 *   <li>只有"已预约(booked)"状态可以退课，已签到 / 已取消 / 已爽约一律 409；</li>
 *   <li>课程<b>开始之后</b>不允许退课 —— 此时应走爽约流程，而不是"退课"；</li>
 *   <li>距开课不足 {@value #WITHDRAW_CUTOFF_HOURS} 小时不允许退课（留出候补时间）。</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/enrollments")
public class EnrollmentController {

    /** 退课截止：距开课不足该小时数则不可退课 */
    public static final int WITHDRAW_CUTOFF_HOURS = 2;

    private final BookingAppService bookingService;
    private final CourseFacade courseFacade;

    public EnrollmentController(BookingAppService bookingService, CourseFacade courseFacade) {
        this.bookingService = bookingService;
        this.courseFacade = courseFacade;
    }

    public record EnrollRequest(Long memberId, Long courseId) {}

    /**
     * 自愿选课（报名）。
     *
     * <p>会员账号无论传什么 memberId，都强制作用于本人；门店后台账号可指定会员代为报名。
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> enroll(@RequestBody EnrollRequest req) {
        Long memberId = AuthContext.effectiveMemberId(req.memberId());
        if (memberId == null) throw new IllegalArgumentException("缺少会员 ID");
        var r = bookingService.book(memberId, req.courseId());
        return ResponseEntity.ok(Map.of(
                "ok", r.ok(),
                "bookingId", r.bookingId() == null ? "" : String.valueOf(r.bookingId()),
                "status", String.valueOf(r.status())));
    }

    /**
     * 我的课表：会员只能看自己的；门店后台可按会员过滤。
     */
    @GetMapping
    public List<BookingView> mySchedule(@RequestParam(required = false) Long memberId) {
        return bookingService.listBookings(AuthContext.effectiveMemberId(memberId));
    }

    /**
     * 自愿退课（退出已报名的课程）。
     *
     * <p>会员只能退本人的预约；课程一旦开始便不可退，需由门店按爽约处理。
     */
    @PostMapping("/{id}/withdraw")
    public ResponseEntity<Map<String, Object>> withdraw(@PathVariable Long id) {
        BookingView view = bookingService.getBooking(id);
        AuthContext.assertSelfOrStaff(view.memberId());
        if (!"booked".equals(view.status())) {
            return ResponseEntity.status(409).body(Map.of(
                    "ok", false,
                    "code", "INVALID_STATE",
                    "reason", "当前状态不可退课：" + view.status()));
        }
        bookingService.cancel(id);
        return ResponseEntity.ok(Map.of("ok", true, "message", "已退课，名额已释放"));
    }
}
