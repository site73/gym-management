package com.gym.venue.api;

import java.util.List;

/**
 * 场馆与场地预约对外契约。
 *
 * <p>业务约束：
 * <ul>
 *   <li>同一场地、同一时段不可重复预约（区间重叠即冲突，沿用 SYS-R3 的思路）；</li>
 *   <li>私有场馆仅对有效会籍开放，公共区域不限制；</li>
 *   <li>会员只能操作本人的场地预约，门店后台可代约与取消。</li>
 * </ul>
 */
public interface VenueFacade {

    /** 全部场馆（会员端展示 + 门店管理） */
    List<VenueView> listVenues();

    /** 单个场馆 */
    VenueView venueOf(Long venueId);

    /** 新增场馆（门店后台） */
    VenueView createVenue(VenueDraft draft);

    /** 修改场馆（门店后台） */
    VenueView updateVenue(Long venueId, VenueDraft draft);

    /**
     * 场地预约列表。
     *
     * @param venueId  按场馆筛选（可空）
     * @param memberId 按会员筛选（可空）；会员账号调用时必须传本人
     */
    List<VenueBookingView> listBookings(Long venueId, Long memberId);

    /** 单个场地预约 */
    VenueBookingView bookingOf(Long bookingId);

    /**
     * 预约场地。
     *
     * @param memberId   预约会员（会员账号由 Controller 归一化为本人）
     * @param operatorId 发起人：会员自助传 null，门店代约传店员 ID
     */
    VenueBookingView book(Long memberId, VenueBookingDraft draft, Long operatorId);

    /** 取消场地预约（会员取消本人的，门店可取消任意） */
    void cancelBooking(Long bookingId);
}
