package com.gym.coach.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 教练对外视图。 */
public record CoachView(
        Long id,
        String code,
        String name,
        String phone,
        String specialty,
        String status,
        Long userId) {

    /** 状态中文名 */
    @JsonProperty("statusCn")
    public String statusCn() {
        return "active".equals(status) ? "在职" : "休假";
    }

    /** 是否已开通登录账号 */
    @JsonProperty("hasAccount")
    public boolean hasAccount() {
        return userId != null;
    }
}
