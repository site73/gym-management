package com.gym.timetable.api;

/**
 * 课表总览：一次请求同时取回「课程课表」与「场馆占用课表」。
 *
 * <p>前端只需按角色请求一次，即可拿到该角色应当看到的两张课表：
 * <ul>
 *   <li>会员：我的课程课表 + 我的场地预约课表；</li>
 *   <li>教练：我的授课课表 + 全店场馆占用课表；</li>
 *   <li>门店后台：全店课程表 + 全店场馆占用课表。</li>
 * </ul>
 *
 * @param courses 课程课表
 * @param venues  场馆占用课表
 * @param note    权限说明（前端直接展示，便于答辩时说明"看到什么由服务端决定"）
 */
public record TimetableOverview(TimetableView courses, TimetableView venues, String note) {
}
