package com.gym.timetable.api;

import com.gym.identity.api.AuthContext;
import com.gym.identity.api.AuthSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 课表入口。
 *
 * <p><b>权限是服务端强制、而不是前端隐藏</b>：会员与教练无论传什么 {@code scope}，
 * 都会被归一化为 {@code mine} 并绑定到本人档案；只有门店后台（店长 / 管理员 / 超级管理员）
 * 才能取到 {@code scope=all} 的全店视图。这样即使直接构造 URL 也拿不到别人的课表。
 *
 * <p>三个只读接口：
 * <ul>
 *   <li>{@code GET /api/timetable/overview} —— 按当前角色返回两张课表（前端主用）；</li>
 *   <li>{@code GET /api/timetable/courses}  —— 课程课表，可指定周与筛选条件；</li>
 *   <li>{@code GET /api/timetable/venues}   —— 场馆占用课表，可指定周与筛选条件。</li>
 * </ul>
 *
 * <p>{@code week} 传该周内任意一天（yyyy-MM-dd），服务端归一到周一；不传则取本周。
 */
@RestController
@RequestMapping("/api/timetable")
public class TimetableController {

    private final TimetableFacade timetableFacade;

    public TimetableController(TimetableFacade timetableFacade) {
        this.timetableFacade = timetableFacade;
    }

    /**
     * 当前用户视角的两张课表。
     *
     * @param week    该周内任意一天，缺省本周
     * @param memberId 按会员筛选（仅门店后台生效）
     * @param coachId  按教练筛选（仅门店后台生效）
     * @param venueId  按场馆筛选场地课表（仅门店后台生效）
     */
    @GetMapping("/overview")
    public TimetableOverview overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) Long coachId,
            @RequestParam(required = false) Long venueId) {
        LocalDate day = week == null ? LocalDate.now() : week;
        AuthSession s = AuthContext.current();

        if (s.isMember()) {
            Long me = AuthContext.effectiveMemberId(null);
            return new TimetableOverview(
                    timetableFacade.courseTimetable(day, "mine", me, null),
                    timetableFacade.venueTimetable(day, "mine", me, null),
                    "会员视角：仅显示本人已选课程与本人的场地预约");
        }
        if (s.isCoach()) {
            Long me = AuthContext.effectiveCoachId(null);
            return new TimetableOverview(
                    timetableFacade.courseTimetable(day, "mine", null, me),
                    timetableFacade.venueTimetable(day, "all", null, venueId),
                    "教练视角：我的授课课表 + 全店场馆占用课表");
        }
        return new TimetableOverview(
                timetableFacade.courseTimetable(day, "all", memberId, coachId),
                timetableFacade.venueTimetable(day, "all", memberId, venueId),
                "门店后台视角：全店课程表 + 全店场馆占用课表" + (memberId == null && coachId == null ? "" : "（已按条件筛选）"));
    }

    /** 课程课表 */
    @GetMapping("/courses")
    public TimetableView courses(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) Long coachId) {
        LocalDate day = week == null ? LocalDate.now() : week;
        AuthSession s = AuthContext.current();

        if (s.isMember()) {
            // 会员只能看本人已选课程：scope 与 memberId 均被服务端改写
            return timetableFacade.courseTimetable(day, "mine", AuthContext.effectiveMemberId(null), null);
        }
        if (s.isCoach()) {
            // 教练只能看本人所授课程：coachId 被服务端改写
            return timetableFacade.courseTimetable(day, "mine", null, AuthContext.effectiveCoachId(null));
        }
        return timetableFacade.courseTimetable(day, "mine".equals(scope) ? "mine" : "all",
                memberId, coachId);
    }

    /** 场馆占用课表 */
    @GetMapping("/venues")
    public TimetableView venues(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) Long venueId) {
        LocalDate day = week == null ? LocalDate.now() : week;
        AuthSession s = AuthContext.current();

        if (s.isMember()) {
            return timetableFacade.venueTimetable(day, "mine", AuthContext.effectiveMemberId(null), null);
        }
        // 教练与门店后台：默认看全店占用情况，可按会员或场馆进一步筛选
        return timetableFacade.venueTimetable(day, "mine".equals(scope) ? "mine" : "all",
                memberId, venueId);
    }
}
