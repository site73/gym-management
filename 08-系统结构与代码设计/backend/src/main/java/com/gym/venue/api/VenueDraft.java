package com.gym.venue.api;

import java.math.BigDecimal;

/**
 * 场馆维护入参（门店后台使用）。
 *
 * @param code      场馆编号（新增时必填且唯一）
 * @param name      名称
 * @param type      private 私有场馆 / public 公共区域，为空默认 private
 * @param capacity  可容纳人数（≥1）
 * @param location  位置说明（可空）
 * @param hourlyFee 按时使用费（可空，默认 0）
 * @param status    available / maintenance，为空默认 available
 */
public record VenueDraft(
        String code,
        String name,
        String type,
        Integer capacity,
        String location,
        BigDecimal hourlyFee,
        String status) {
}
