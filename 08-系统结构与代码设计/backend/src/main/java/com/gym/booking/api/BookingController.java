package com.gym.booking.api;

import com.gym.booking.application.BookingAppService;
import com.gym.course.api.CourseFacade;
import com.gym.identity.api.AuthContext;
import com.gym.identity.api.AuthException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 约课 REST 入口。
 *
 * <p>权限约束（服务端强制，不依赖前端）：
 * <ul>
 *   <li>会员账号：只能查/约/取消**自己**的预约；判爽约属门店操作</li>
 *   <li>门店后台：可查看全部、按会员过滤、判爽约</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingAppService bookingService;
    private final CourseFacade courseFacade;

    public BookingController(BookingAppService bookingService, CourseFacade courseFacade) {
        this.bookingService = bookingService;
        this.courseFacade = courseFacade;
    }

    /** 是否本人、门店后台、或该课程的任课教练 */
    private boolean canOperate(BookingView view) {
        var s = AuthContext.current();
        if (s.isStaff()) return true;
        if (s.isMember()) return s.memberId() != null && s.memberId().equals(view.memberId());
        if (s.isCoach() && s.coachId() != null) {
            return s.coachId().equals(courseFacade.courseOf(view.courseId()).coachId());
        }
        return false;
    }

    public record BookRequest(Long memberId, Long courseId) {}
    public record CheckinRequest(String channel) {}

    /** 预约列表：会员只能看到自己的 */
    @GetMapping
    public List<BookingView> list(@RequestParam(required = false) Long memberId) {
        return bookingService.listBookings(AuthContext.effectiveMemberId(memberId));
    }

    /** 预约详情 */
    @GetMapping("/{id}")
    public BookingView detail(@PathVariable Long id) {
        BookingView view = bookingService.getBooking(id);
        AuthContext.assertSelfOrStaff(view.memberId());
        return view;
    }

    /** 约课（会员端发起时忽略传入的 memberId，强制本人） */
    @PostMapping
    public ResponseEntity<Map<String, Object>> book(@RequestBody BookRequest req) {
        Long memberId = AuthContext.effectiveMemberId(req.memberId());
        if (memberId == null) throw new IllegalArgumentException("缺少会员 ID");
        var r = bookingService.book(memberId, req.courseId());
        return r.ok()
                ? ResponseEntity.ok(Map.of("ok", true, "bookingId", r.bookingId(),
                        "status", String.valueOf(r.status())))
                : ResponseEntity.status(409).body(Map.of("ok", false,
                        "ruleCode", String.valueOf(r.ruleCode()), "reason", String.valueOf(r.reason())));
    }

    /** 签到：本人、门店代签，或该课程的任课教练核销 */
    @PostMapping("/{id}/checkin")
    public ResponseEntity<Void> checkIn(@PathVariable Long id, @RequestBody(required = false) CheckinRequest req) {
        BookingView view = bookingService.getBooking(id);
        if (!canOperate(view)) {
            throw AuthException.forbidden("只能核销本人或自己课程的签到");
        }
        String channel = (req == null || req.channel() == null || req.channel().isBlank())
                ? (AuthContext.current().isStaff() ? "front_desk" : "scan") : req.channel();
        Long operatorId = AuthContext.current().isStaff() ? AuthContext.current().userId() : null;
        bookingService.checkIn(id, channel, operatorId);
        return ResponseEntity.noContent().build();
    }

    /** 取消：本人或门店 */
    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable Long id) {
        BookingView view = bookingService.getBooking(id);
        AuthContext.assertSelfOrStaff(view.memberId());
        bookingService.cancel(id);
        return Map.of("ok", true);
    }

    /** 判爽约：仅门店后台（REQ-B4-003） */
    @PostMapping("/{id}/no-show")
    public Map<String, Object> noShow(@PathVariable Long id) {
        AuthContext.requireStaff();
        return Map.of("ok", true, "noShowCount", bookingService.markNoShow(id));
    }
}
