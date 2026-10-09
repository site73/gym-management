package com.gym.venue.api;

import java.math.BigDecimal;

/** 场馆 / 区域视图。 */
public record VenueView(
        Long id,
        String code,
        String name,
        String type,
        int capacity,
        String location,
        BigDecimal hourlyFee,
        String status) {

    /** private 私有场馆 / public 公共区域 */
    public String typeCn() {
        return isPublic() ? "公共区域" : "私有场馆";
    }

    public String statusCn() {
        return "available".equals(status) ? "可用" : "维护中";
    }

    public boolean isPublic() {
        return "public".equals(type);
    }

    /** 是否收费（私有场馆通常按时收费） */
    public boolean chargeable() {
        return hourlyFee != null && hourlyFee.signum() > 0;
    }
}
