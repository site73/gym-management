package com.gym.shared.audit;

import com.gym.identity.api.AuthContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 审计日志查询接口（门店后台）。 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditQueryService auditQueryService;

    public AuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping
    public List<AuditQueryService.AuditRow> recent() {
        AuthContext.requireStaff();
        return auditQueryService.recent();
    }
}
