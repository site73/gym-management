package com.gym.timetable.api;

import java.time.LocalDate;
import java.util.List;

/**
 * 周课表视图（课程课表与场地占用课表共用同一结构）。
 *
 * @param weekStart 本周起始日（周一）
 * @param weekEnd   本周结束日（周日）
 * @param scope     数据范围：mine（本人）/ all（全店，仅门店后台）
 * @param owner     课表归属描述，如"我的课表（张三）"或"全店课程表"
 * @param days      按天分组的条目（固定 7 天，无条目的日期 items 为空）
 */
public record TimetableView(
        LocalDate weekStart,
        LocalDate weekEnd,
        String scope,
        String owner,
        List<Day> days) {

    /** 单日条目 */
    public record Day(
            LocalDate date,
            /** 周一 ~ 周日 */
            String weekdayCn,
            /** 是否今天 */
            boolean today,
            List<Item> items) {
    }

    /**
     * 课表条目：课程或场地占用。
     *
     * @param kind     course 课程 / venue 场地占用
     * @param id       课程 ID 或场地预约 ID
     * @param title    课程名或场馆名
     * @param code     课程编号或场馆编号
     * @param subtitle 副标题（教练 / 会员 / 场地）
     * @param start    开始时间 HH:mm
     * @param end      结束时间 HH:mm
     * @param capacity 容量（课程有值，场地为 0）
     * @param booked   已约人数（课程有值）
     * @param remaining 余位（课程有值）
     * @param date     所属日期（便于前端直接分组与排序）
     * @param statusCn 状态中文
     * @param mine     该条目是否与当前登录者本人相关（用于高亮）
     */
    public record Item(
            String kind,
            Long id,
            LocalDate date,
            String title,
            String code,
            String subtitle,
            String start,
            String end,
            int capacity,
            int booked,
            int remaining,
            String statusCn,
            boolean mine) {
    }
}
