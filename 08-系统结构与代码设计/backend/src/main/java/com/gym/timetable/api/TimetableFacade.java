package com.gym.timetable.api;

import java.time.LocalDate;
import java.util.List;

/**
 * 课表契约：把课程与场地预约聚合为"周课表"视图。
 *
 * <p>权限语义（由 Controller 强制）：
 * <ul>
 *   <li>会员：只能取 {@code scope=mine}——本人已选课程 + 本人场地预约；</li>
 *   <li>教练：只能取 {@code scope=mine}——本人所授课程；</li>
 *   <li>门店后台：可取 {@code scope=all}——全店课程表 / 场馆占用课表，并可按会员或教练筛选。</li>
 * </ul>
 */
public interface TimetableFacade {

    /**
     * 课程课表。
     *
     * @param weekStart 任意一天，内部会归一到该周周一
     * @param scope     mine / all
     * @param memberId  按会员筛选（仅门店后台可用，scope=mine 时忽略）
     * @param coachId   按教练筛选（仅门店后台可用，scope=mine 时忽略）
     */
    TimetableView courseTimetable(LocalDate weekStart, String scope, Long memberId, Long coachId);

    /** 场地占用课表 */
    TimetableView venueTimetable(LocalDate weekStart, String scope, Long memberId, Long venueId);

    /** 本周起始日（周一） */
    static LocalDate mondayOf(LocalDate anyDay) {
        return anyDay.minusDays(anyDay.getDayOfWeek().getValue() - 1L);
    }

    static List<String> weekdaysCn() {
        return List.of("周一", "周二", "周三", "周四", "周五", "周六", "周日");
    }
}
