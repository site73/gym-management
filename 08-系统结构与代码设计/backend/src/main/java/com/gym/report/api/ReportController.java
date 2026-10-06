package com.gym.report.api;

import com.gym.identity.api.AuthContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 报表接口（门店后台）。 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportFacade reportFacade;

    public ReportController(ReportFacade reportFacade) {
        this.reportFacade = reportFacade;
    }

    /** 经营摘要 */
    @GetMapping("/summary")
    public ReportViews.Summary summary() {
        AuthContext.requireStaff();
        return reportFacade.summary();
    }
}
