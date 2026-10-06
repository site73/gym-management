package com.gym.warning.api;

import com.gym.identity.api.AuthContext;
import org.springframework.web.bind.annotation.*;

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

    /**
     * 预警任务与统计（实时计算，避免读到过期任务）。
     *
     * <p>返回完整 {@link WarningViews.RiskSummary} 而非纯列表：
     * 这样"打开标签页自动加载"与"手动执行扫描"两条路径渲染内容一致，
     * 不会因响应先后顺序不同而互相覆盖（曾导致前端偶发无内容）。
     */
    @GetMapping("/risks")
    public WarningViews.RiskSummary risks() {
        AuthContext.requireStaff();
        return warningFacade.riskScan();
    }

    /** 爽约预测（SYS-R8） */
    @GetMapping("/predictions")
    public WarningViews.PredictionResult predictions(@RequestParam(required = false) Long courseId) {
        AuthContext.requireStaff();
        return warningFacade.predict(courseId);
    }
}
