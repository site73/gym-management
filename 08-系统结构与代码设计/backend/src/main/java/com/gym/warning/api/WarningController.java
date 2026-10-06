package com.gym.warning.api;

import com.gym.identity.api.AuthContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 预警接口（门店后台使用）。 */
@RestController
@RequestMapping("/api")
public class WarningController {

    private final WarningFacade warningFacade;

    public WarningController(WarningFacade warningFacade) {
        this.warningFacade = warningFacade;
    }

    /** 执行风险扫描（SYS-R7 / SYS-R9） */
    @PostMapping("/jobs/risk-score")
    public WarningViews.RiskSummary scan() {
        AuthContext.requireStaff();
        return warningFacade.riskScan();
    }

    /** 预警任务列表（实时计算，避免读到过期任务） */
    @GetMapping("/risks")
    public List<WarningViews.RiskTask> risks() {
        AuthContext.requireStaff();
        return warningFacade.riskScan().tasks();
    }

    /** 爽约预测（SYS-R8） */
    @GetMapping("/predictions")
    public WarningViews.PredictionResult predictions(@RequestParam(required = false) Long courseId) {
        AuthContext.requireStaff();
        return warningFacade.predict(courseId);
    }
}
