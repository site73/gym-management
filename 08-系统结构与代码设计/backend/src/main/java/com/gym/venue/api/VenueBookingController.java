package com.gym.venue.api;

import com.gym.identity.api.AuthContext;
import com.gym.identity.api.AuthException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 场地预约入口。
 *
 * <p>权限约束（服务端强制）：
 * <ul>
 *   <li>会员账号：只能查 / 约 / 取消<b>本人</b>的场地预约（伪造 memberId 会被改写）；</li>
 *   <li>门店后台：可查看全部、代客预约、取消任意预约。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/venue-bookings")
public class VenueBookingController {

    private final VenueFacade venueFacade;

    public VenueBookingController(VenueFacade venueFacade) {
        this.venueFacade = venueFacade;
    }

    /** 预约列表：会员只能看自己的；门店可按场馆或会员筛选 */
    @GetMapping
    public List<VenueBookingView> list(@RequestParam(required = false) Long venueId,
                                       @RequestParam(required = false) Long memberId) {
        var s = AuthContext.current();
        if (s.isMember()) {
            return venueFacade.listBookings(venueId, AuthContext.effectiveMemberId(null));
        }
        return venueFacade.listBookings(venueId, memberId);
    }

    /** 预约详情 */
    @GetMapping("/{id}")
    public VenueBookingView detail(@PathVariable Long id) {
        VenueBookingView view = venueFacade.bookingOf(id);
        AuthContext.assertSelfOrStaff(view.memberId());
        return view;
    }

    /** 预约场地（会员自助；门店可代约，需传 memberId） */
    @PostMapping
    public ResponseEntity<VenueBookingView> book(@RequestBody VenueBookingDraft draft) {
        Long memberId = AuthContext.effectiveMemberId(draft.memberId());
        if (memberId == null) {
            throw new IllegalArgumentException("缺少会员 ID");
        }
        Long operatorId = AuthContext.current().isStaff() ? AuthContext.current().userId() : null;
        return ResponseEntity.ok(venueFacade.book(memberId, draft, operatorId));
    }

    /** 取消预约：本人或门店后台 */
    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable Long id) {
        VenueBookingView view = venueFacade.bookingOf(id);
        var s = AuthContext.current();
        boolean self = s.isMember() && s.memberId() != null && s.memberId().equals(view.memberId());
        if (!s.isStaff() && !self) {
            throw AuthException.forbidden("只能取消本人的场地预约");
        }
        venueFacade.cancelBooking(id);
        return Map.of("ok", true, "message", "场地预约已取消");
    }
}
