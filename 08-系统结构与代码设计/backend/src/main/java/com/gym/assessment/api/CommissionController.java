package com.gym.assessment.api;

import com.gym.identity.api.AuthContext;
import org.springframework.web.bind.annotation.*;

/** 私教提成接口（门店后台）。 */
@RestController
@RequestMapping("/api/commissions")
public class CommissionController {

    private final CommissionFacade commissionFacade;

    public CommissionController(CommissionFacade commissionFacade) {
        this.commissionFacade = commissionFacade;
    }

    /** 提成核算（默认近 30 天） */
    @GetMapping
    public CommissionViews.CommissionReport commissions(@RequestParam(defaultValue = "30") int days) {
        AuthContext.requireStaff();
        return commissionFacade.monthly(days);
    }
}
