package com.gym.timetable;

import com.gym.booking.api.BookingFacade;
import com.gym.booking.api.BookingView;
import com.gym.coach.api.CoachFacade;
import com.gym.coach.api.CoachView;
import com.gym.course.api.CourseFacade;
import com.gym.course.api.CourseView;
import com.gym.membership.api.MemberView;
import com.gym.membership.api.MembershipFacade;
import com.gym.timetable.api.TimetableFacade;
import com.gym.timetable.api.TimetableView;
import com.gym.timetable.internal.TimetableServiceImpl;
import com.gym.venue.api.VenueBookingView;
import com.gym.venue.api.VenueFacade;
import com.gym.venue.api.VenueView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 周课表单元测试（课程课表 + 场馆占用课表）。
 *
 * <p>重点验证四件事：
 * <ol>
 *   <li>任意一天都能归一到<b>周一</b>，且固定返回 7 天（空档也保留）；</li>
 *   <li>{@code scope=mine} 只出现本人相关条目，并打上 {@code mine} 高亮；</li>
 *   <li>{@code scope=all} 时按会员 / 教练 / 场馆筛选生效；</li>
 *   <li>课表是<b>只读聚合视图</b>：只展示占用中的场地预约，已取消的不再占位。</li>
 * </ol>
 */
class TimetableTest {

    private final CourseFacade courseFacade = mock(CourseFacade.class);
    private final CoachFacade coachFacade = mock(CoachFacade.class);
    private final VenueFacade venueFacade = mock(VenueFacade.class);
    private final BookingFacade bookingFacade = mock(BookingFacade.class);
    private final MembershipFacade membershipFacade = mock(MembershipFacade.class);
    private final TimetableFacade facade = new TimetableServiceImpl(
            courseFacade, coachFacade, venueFacade, bookingFacade, membershipFacade);

    /* 本周：以"今天"所在周为基准 */
    private static final LocalDate MONDAY = TimetableFacade.mondayOf(LocalDate.now());
    private static final LocalDateTime MON_1900 = LocalDateTime.of(MONDAY, LocalTime.of(19, 0));
    private static final LocalDateTime WED_1800 = LocalDateTime.of(MONDAY.plusDays(2), LocalTime.of(18, 0));
    private static final LocalDateTime NEXT_MON = MONDAY.plusWeeks(1).atTime(19, 0);

    private CourseView course(Long id, String code, String name, Long coachId, LocalDateTime start, int cap, int booked) {
        return new CourseView(id, code, name, "group", coachId, "A 厅", start, start.plusHours(1),
                cap, booked, cap - booked, booked >= cap ? "full" : "published");
    }

    private MemberView member(Long id, String no, String name) {
        return new MemberView(id, no, name, "138****0001", "active", "low", 20, 0, LocalDateTime.now());
    }

    private void givenCourses() {
        when(courseFacade.listCourses()).thenReturn(List.of(
                course(11L, "C001", "动感单车", 101L, MON_1900, 20, 18),
                course(12L, "C002", "瑜伽", 102L, WED_1800, 15, 15),
                course(13L, "C003", "搏击操", 101L, NEXT_MON, 12, 5)   // 下一周，本周课表不应出现
        ));
        when(coachFacade.listCoaches()).thenReturn(List.of(
                new CoachView(101L, "T01", "王教练", "13800000001", "力量", "active", 9001L),
                new CoachView(102L, "T02", "李教练", "13800000002", "瑜伽", "active", 9002L)));
        when(coachFacade.coachOf(101L))
                .thenReturn(new CoachView(101L, "T01", "王教练", "13800000001", "力量", "active", 9001L));
        when(coachFacade.coachOf(102L))
                .thenReturn(new CoachView(102L, "T02", "李教练", "13800000002", "瑜伽", "active", 9002L));
    }

    private void givenVenues() {
        when(venueFacade.listVenues()).thenReturn(List.of(
                new VenueView(1L, "V01", "力量训练室", "private", 6, "二层东侧", new BigDecimal("60.00"), "available"),
                new VenueView(5L, "V05", "自由力量区", "public", 20, "一层中庭", BigDecimal.ZERO, "available")));
        when(venueFacade.listBookings(any(), any())).thenReturn(List.of(
                new VenueBookingView(41L, 1L, "力量训练室", "private", 1L, "张三",
                        LocalDateTime.of(MONDAY, LocalTime.of(10, 0)), LocalDateTime.of(MONDAY, LocalTime.of(11, 0)),
                        "booked", null),
                new VenueBookingView(42L, 1L, "力量训练室", "private", 2L, "李四",
                        LocalDateTime.of(MONDAY, LocalTime.of(14, 0)), LocalDateTime.of(MONDAY, LocalTime.of(15, 0)),
                        "cancelled", null),                                  // 已取消：不占位
                new VenueBookingView(43L, 5L, "自由力量区", "public", 3L, "王五",
                        LocalDateTime.of(MONDAY.plusDays(3), LocalTime.of(16, 0)),
                        LocalDateTime.of(MONDAY.plusDays(3), LocalTime.of(17, 0)), "booked", 5L)
        ));
    }

    private int count(TimetableView tt) {
        return tt.days().stream().mapToInt(d -> d.items().size()).sum();
    }

    @Test
    @DisplayName("TT-01 任意一天都归一到周一，且固定返回 7 天")
    void weekAlwaysStartsOnMondayAndHasSevenDays() {
        givenCourses();
        for (int offset = 0; offset < 7; offset++) {
            TimetableView tt = facade.courseTimetable(MONDAY.plusDays(offset), "all", null, null);
            assertThat(tt.weekStart()).as("无论传周内哪一天，都归一到周一").isEqualTo(MONDAY);
            assertThat(tt.weekEnd()).isEqualTo(MONDAY.plusDays(6));
            assertThat(tt.days()).hasSize(7);
            assertThat(tt.days().get(0).weekdayCn()).isEqualTo("周一");
            assertThat(tt.days().get(6).weekdayCn()).isEqualTo("周日");
        }
    }

    @Test
    @DisplayName("TT-02 只统计本周：下周的课程不出现在本周课表")
    void onlyCurrentWeekIsIncluded() {
        givenCourses();
        TimetableView tt = facade.courseTimetable(MONDAY, "all", null, null);
        assertThat(count(tt)).isEqualTo(2);                       // C001 / C002 在本周，C003 在下一周
        assertThat(tt.days().stream().flatMap(d -> d.items().stream()).map(TimetableView.Item::code))
                .containsExactlyInAnyOrder("C001", "C002");
    }

    @Test
    @DisplayName("TT-03 scope=all 时本周课表含全部课程，条目按开始时间排序")
    void allScopeShowsEverything() {
        givenCourses();
        TimetableView tt = facade.courseTimetable(MONDAY, "all", null, null);
        assertThat(tt.scope()).isEqualTo("all");
        assertThat(tt.owner()).contains("全店课程表");
        List<TimetableView.Item> monday = tt.days().get(0).items();
        assertThat(monday).hasSize(1);
        assertThat(monday.get(0).title()).isEqualTo("动感单车");
        assertThat(monday.get(0).subtitle()).contains("王教练");
        assertThat(monday.get(0).remaining()).isEqualTo(2);
        assertThat(monday.get(0).mine()).isFalse();               // 未指定会员/教练时不打高亮
    }

    @Test
    @DisplayName("TT-04 会员视角 scope=mine：只出现本人已选课程并高亮")
    void memberMineScopeShowsOnlyOwnCourses() {
        givenCourses();
        when(bookingFacade.listBookings(1L)).thenReturn(List.of(
                new BookingView(1L, 1L, 11L, "booked", null, null, LocalDateTime.now(), null)));
        when(membershipFacade.memberOf(anyLong()))
                .thenReturn(member(1L, "M001", "张三"));

        TimetableView tt = facade.courseTimetable(MONDAY, "mine", 1L, null);
        assertThat(tt.scope()).isEqualTo("mine");
        assertThat(count(tt)).isEqualTo(1);
        TimetableView.Item item = tt.days().get(0).items().get(0);
        assertThat(item.code()).isEqualTo("C001");
        assertThat(item.mine()).isTrue();
    }

    @Test
    @DisplayName("TT-05 教练视角 scope=mine：只出现本人所授课程")
    void coachMineScopeShowsOnlyOwnCourses() {
        givenCourses();
        TimetableView tt = facade.courseTimetable(MONDAY, "mine", null, 101L);
        assertThat(count(tt)).isEqualTo(1);                        // 王教练本周只在周一有课
        assertThat(tt.days().get(0).items().get(0).code()).isEqualTo("C001");
        assertThat(tt.owner()).contains("王教练");
    }

    @Test
    @DisplayName("TT-06 门店后台按教练筛选：只出现该教练的课")
    void staffCanFilterByCoach() {
        givenCourses();
        TimetableView tt = facade.courseTimetable(MONDAY, "all", null, 102L);
        assertThat(count(tt)).isEqualTo(1);
        assertThat(tt.days().get(2).items().get(0).code()).isEqualTo("C002");   // 周三
        assertThat(tt.owner()).contains("李教练");
    }

    @Test
    @DisplayName("TT-07 mine 且未绑定档案 → 空课表（不越权返回全店数据）")
    void mineScopeWithoutOwnerReturnsEmpty() {
        givenCourses();
        assertThat(count(facade.courseTimetable(MONDAY, "mine", null, null))).isZero();
    }

    @Test
    @DisplayName("TT-08 场馆占用课表：只显示 booked，已取消的不占位")
    void venueTimetableSkipsCancelled() {
        givenVenues();
        TimetableView tt = facade.venueTimetable(MONDAY, "all", null, null);
        assertThat(tt.owner()).contains("全店");
        assertThat(count(tt)).isEqualTo(2);                        // 41 与 43，42 已取消
        assertThat(tt.days().stream().flatMap(d -> d.items().stream()).map(TimetableView.Item::id))
                .containsExactlyInAnyOrder(41L, 43L);
        assertThat(tt.days().get(0).items().get(0).kind()).isEqualTo("venue");
        assertThat(tt.days().get(0).items().get(0).subtitle()).contains("张三");
    }

    @Test
    @DisplayName("TT-09 会员场地课表 mine：只看本人，且 mine 高亮")
    void memberVenueTimetableIsPersonal() {
        givenVenues();
        when(venueFacade.listBookings(null, 1L)).thenReturn(List.of(
                new VenueBookingView(41L, 1L, "力量训练室", "private", 1L, "张三",
                        LocalDateTime.of(MONDAY, LocalTime.of(10, 0)),
                        LocalDateTime.of(MONDAY, LocalTime.of(11, 0)), "booked", null)));
        TimetableView tt = facade.venueTimetable(MONDAY, "mine", 1L, null);
        assertThat(count(tt)).isEqualTo(1);
        assertThat(tt.days().get(0).items().get(0).mine()).isTrue();
        assertThat(tt.owner()).contains("我的场地预约课表");
    }

    @Test
    @DisplayName("TT-10 场馆占用课表按场馆筛选")
    void venueTimetableFilteredByVenue() {
        givenVenues();
        when(venueFacade.listBookings(5L, null)).thenReturn(List.of(
                new VenueBookingView(43L, 5L, "自由力量区", "public", 3L, "王五",
                        LocalDateTime.of(MONDAY.plusDays(3), LocalTime.of(16, 0)),
                        LocalDateTime.of(MONDAY.plusDays(3), LocalTime.of(17, 0)), "booked", 5L)));
        TimetableView tt = facade.venueTimetable(MONDAY, "all", null, 5L);
        assertThat(count(tt)).isEqualTo(1);
        assertThat(tt.days().get(3).items().get(0).subtitle()).contains("公共区域");
        assertThat(tt.owner()).contains("自由力量区");
    }

    @Test
    @DisplayName("TT-11 今天的那一列标记为 today，便于前端高亮")
    void todayIsMarked() {
        givenCourses();
        TimetableView tt = facade.courseTimetable(MONDAY, "all", null, null);
        long todayCount = tt.days().stream().filter(TimetableView.Day::today).count();
        assertThat(todayCount).isEqualTo(1);
        assertThat(tt.days().stream().filter(TimetableView.Day::today).findFirst().orElseThrow().date())
                .isEqualTo(LocalDate.now());
    }
}
