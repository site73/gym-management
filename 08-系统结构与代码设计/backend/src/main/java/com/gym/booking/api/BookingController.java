package com.gym.booking.api;

import com.gym.booking.application.BookingAppService;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 预约 REST 入口（小程序端与前台后台共用）。
 *
 * <p>错误语义对齐《接口契约初步清单》：
 * 409 BOOKING_REJECTED + reason（会籍过期 / 课程已满 / 时间冲突 / 爽约限制）。
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingAppService bookingAppService;

    public BookingController(BookingAppService bookingAppService) {
        this.bookingAppService = bookingAppService;
    }

    public record BookRequest(@NotNull Long memberId, @NotNull Long courseId) {}
    public record CheckInRequest(String channel, Long operatorId) {}

    /** 约课（REQ-B4-001） */
    @PostMapping
    public ResponseEntity<?> book(@RequestBody BookRequest req) {
        var result = bookingAppService.book(req.memberId(), req.courseId());
        if (!result.ok()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new RejectBody("BOOKING_REJECTED", result.ruleCode(), result.reason()));
        }
        return ResponseEntity.ok(result);
    }

    /** 签到（会员扫码或前台代签，REQ-B4-002 / REQ-B4-005） */
    @PostMapping("/{id}/checkin")
    public ResponseEntity<Void> checkIn(@PathVariable Long id,
                                        @RequestBody(required = false) CheckInRequest req) {
        String channel = (req == null || req.channel() == null) ? "scan" : req.channel();
        Long operatorId = req == null ? null : req.operatorId();
        bookingAppService.checkIn(id, channel, operatorId);
        return ResponseEntity.noContent().build();
    }

    /** 取消预约（SYS-R3：释放名额） */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        bookingAppService.cancel(id);
        return ResponseEntity.noContent().build();
    }

    /** 爽约判定（内部/定时任务调用，REQ-B4-003） */
    @PostMapping("/{id}/no-show")
    public ResponseEntity<Integer> markNoShow(@PathVariable Long id) {
        return ResponseEntity.ok(bookingAppService.markNoShow(id));
    }

    public record RejectBody(String code, String ruleCode, String reason) {}
}
