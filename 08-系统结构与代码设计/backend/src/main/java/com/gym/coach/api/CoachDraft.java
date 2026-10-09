package com.gym.coach.api;

/**
 * 教练档案入参（门店后台维护教练时使用）。
 *
 * @param code      教练编号（新增时必填且唯一）
 * @param name      姓名
 * @param phone     手机号（可空）
 * @param specialty 擅长项目（可空）
 * @param status    active / leave，为空默认 active
 */
public record CoachDraft(
        String code,
        String name,
        String phone,
        String specialty,
        String status) {
}
