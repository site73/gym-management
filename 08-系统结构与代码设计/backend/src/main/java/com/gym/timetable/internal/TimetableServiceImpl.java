package com.gym.timetable.internal;

import com.gym.booking.api.BookingFacade;
import com.gym.booking.api.BookingView;
import com.gym.coach.api.CoachFacade;
import com.gym.course.api.CourseFacade;
import com.gym.course.api.CourseView;
import com.gym.membership.api.MembershipFacade;
import com.gym.timetable.api.TimetableFacade;
import com.gym.timetable.api.TimetableView;
import com.gym.venue.api.VenueBookingView;
import com.gym.venue.api.VenueFacade;
import com.gym.venue.api.VenueView;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 课表聚合实现。
 *
 * <p>设计要点：课表是**只读聚合视图**，本身不持有数据；所有条目都来自各模块的对外契约
 * （{@link CourseFacade}、{@link VenueFacade}、{@link BookingFacade}），
 * 因此不会出现"课表与业务数据不一致"的问题。
 *
 * <p>性能：各契约方法返回的是内存列表（课程与场地量级很小），
 * 一次周查询只需 4~5 次调用即可完成装配。
 */
@Service
public class TimetableServiceImpl implements TimetableFacade {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final CourseFacade courseFacade;
    private final CoachFacade coachFacade;
    private final VenueFacade venueFacade;
    private final BookingFacade bookingFacade;
    private final MembershipFacade membershipFacade;

    public TimetableServiceImpl(CourseFacade courseFacade, CoachFacade coachFacade,
                                VenueFacade venueFacade, BookingFacade bookingFacade,
                                MembershipFacade membershipFacade) {
        this.courseFacade = courseFacade;
        this.coachFacade = coachFacade;
        this.venueFacade = venueFacade;
        this.bookingFacade = bookingFacade;
        this.membershipFacade = membershipFacade;
    }

    /* ==================== 课程课表 ==================== */

    @Override
    public TimetableView courseTimetable(LocalDate anyDay, String scope, Long memberId, Long coachId) {
        LocalDate monday = TimetableFacade.mondayOf(anyDay);
        LocalDate sunday = monday.plusDays(6);
        boolean mineScope = "mine".equals(scope);

        Map<Long, String> coachNames = coachFacade.listCoaches().stream()
                .collect(Collectors.toMap(c -> c.id(), c -> c.name(), (a, b) -> a));

        // 会员：取出本人已报名（有效状态）的课程，用于过滤与高亮
        Set<Long> myCourseIds = new HashSet<>();
        if (memberId != null) {
            for (BookingView b : bookingFacade.listBookings(memberId)) {
                if ("booked".equals(b.status()) || "checked_in".equals(b.status())) {
                    myCourseIds.add(b.courseId());
                }
            }
        }

        List<TimetableView.Item> all = new ArrayList<>();
        for (CourseView c : courseFacade.listCourses()) {
            if (!inRange(c.startTime(), monday, sunday)) continue;

            // 过滤规则：同时给了会员与教练 → 取交集；只给一个 → 按该条件；
            // 都没给时——scope=mine 视为"空课表"（调用方应先绑定档案），scope=all 则全量。
            boolean keep;
            if (coachId != null && memberId != null) {
                keep = coachId.equals(c.coachId()) && myCourseIds.contains(c.id());
            } else if (coachId != null) {
                keep = coachId.equals(c.coachId());
            } else if (memberId != null) {
                keep = myCourseIds.contains(c.id());
            } else {
                keep = !mineScope;
            }
            if (!keep) continue;

            boolean mine = (coachId != null && coachId.equals(c.coachId()))
                    || (memberId != null && myCourseIds.contains(c.id()));
            all.add(new TimetableView.Item(
                    "course", c.id(), c.startTime().toLocalDate(), c.name(), c.code(),
                    "教练 " + coachNames.getOrDefault(c.coachId(), "待定")
                            + (c.room() == null ? "" : " · " + c.room()),
                    c.startTime().format(HM), c.endTime().format(HM),
                    c.capacity(), c.bookedCount(), c.remaining(),
                    c.remaining() > 0 ? "余 " + c.remaining() : "已满", mine));
        }

        String owner = ownerLabel(mineScope, memberId, coachId);
        return assemble(monday, sunday, scope, owner, all);
    }

    /* ==================== 场地占用课表 ==================== */

    @Override
    public TimetableView venueTimetable(LocalDate anyDay, String scope, Long memberId, Long venueId) {
        LocalDate monday = TimetableFacade.mondayOf(anyDay);
        LocalDate sunday = monday.plusDays(6);
        boolean mineScope = "mine".equals(scope);

        Map<Long, VenueView> venueMap = venueFacade.listVenues().stream()
                .collect(Collectors.toMap(v -> v.id(), v -> v, (a, b) -> a));

        // 会员看本人（mine 且未绑定档案 → 空课表，避免越权拿到全店数据）；
        // 门店后台/教练看全部，可按场馆或会员继续筛选。
        List<VenueBookingView> bookings;
        if (mineScope && memberId == null) {
            bookings = List.of();
        } else {
            bookings = venueFacade.listBookings(venueId, memberId);
        }

        List<TimetableView.Item> all = new ArrayList<>();
        for (VenueBookingView b : bookings) {
            if (!"booked".equals(b.status())) continue;        // 课表只展示占用中的时段
            if (!inRange(b.startTime(), monday, sunday)) continue;
            VenueView v = venueMap.get(b.venueId());
            String typeCn = v != null && v.isPublic() ? "公共区域" : "私有场馆";
            all.add(new TimetableView.Item(
                    "venue", b.id(), b.startTime().toLocalDate(),
                    b.venueName() == null ? ("#" + b.venueId()) : b.venueName(),
                    v == null ? "—" : v.code(),
                    typeCn + " · " + b.memberName() + "（" + ("门店代约".equals(b.sourceCn()) ? "门店代约" : "会员自助") + "）",
                    b.startTime().format(HM), b.endTime().format(HM),
                    0, 0, 0, "已预约",
                    memberId != null && memberId.equals(b.memberId())));
        }

        String owner = venueOwnerLabel(mineScope, memberId, venueId, venueMap);
        return assemble(monday, sunday, scope, owner, all);
    }

    /* ==================== 内部 ==================== */

    private boolean inRange(LocalDateTime t, LocalDate from, LocalDate to) {
        if (t == null) return false;
        LocalDate d = t.toLocalDate();
        return !d.isBefore(from) && !d.isAfter(to);
    }

    /** 课程课表归属文案：mine 用"我的课表"，all 带筛选条件时写明筛的是谁 */
    private String ownerLabel(boolean mineScope, Long memberId, Long coachId) {
        if (coachId != null) {
            String n = safeCoachName(coachId);
            return mineScope ? "我的课表（教练 " + n + "）" : "教练课表（" + n + "）";
        }
        if (memberId != null) {
            String n = safeMemberName(memberId);
            return mineScope ? "我的课表（" + n + "）" : "会员课表（" + n + "）";
        }
        return mineScope ? "我的课表" : "全店课程表";
    }

    /** 场馆占用课表归属文案 */
    private String venueOwnerLabel(boolean mineScope, Long memberId, Long venueId,
                                   Map<Long, VenueView> venueMap) {
        if (mineScope) return "我的场地预约课表";
        if (venueId != null) {
            VenueView v = venueMap.get(venueId);
            return "场馆占用课表（" + (v == null ? "#" + venueId : v.name()) + "）";
        }
        if (memberId != null) {
            return "场馆占用课表（会员 " + safeMemberName(memberId) + "）";
        }
        return "场馆占用课表（全店）";
    }

    private String safeCoachName(Long coachId) {
        try {
            return coachFacade.coachOf(coachId).name();
        } catch (RuntimeException e) {
            return "#" + coachId;
        }
    }

    private String safeMemberName(Long memberId) {
        try {
            return membershipFacade.memberOf(memberId).name();
        } catch (RuntimeException e) {
            return "#" + memberId;
        }
    }

    /** 按天分组并补齐 7 天（条目自带日期，无条目的日期保留空数组） */
    private TimetableView assemble(LocalDate monday, LocalDate sunday, String scope,
                                   String owner, List<TimetableView.Item> items) {
        LocalDate today = LocalDate.now();
        List<String> names = TimetableFacade.weekdaysCn();
        List<TimetableView.Day> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = monday.plusDays(i);
            List<TimetableView.Item> ofDay = items.stream()
                    .filter(it -> d.equals(it.date()))
                    .sorted(Comparator.comparing(TimetableView.Item::start))
                    .toList();
            days.add(new TimetableView.Day(d, names.get(i), d.equals(today), ofDay));
        }
        return new TimetableView(monday, sunday, scope, owner, days);
    }
}
