package com.gym.system.api;

import com.gym.identity.api.AuthContext;
import com.gym.shared.audit.AuditLogger;
import com.gym.system.internal.DemoDataResetter;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 系统管理接口（仅管理员）。 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final DemoDataResetter resetter;
    private final AuditLogger auditLogger;

    public AdminController(DemoDataResetter resetter, AuditLogger auditLogger) {
        this.resetter = resetter;
        this.auditLogger = auditLogger;
    }

    /** 重置演示数据（危险操作，仅 admin 角色） */
    @PostMapping("/reset")
    public Map<String, Object> reset() {
        var session = AuthContext.requireAdmin();
        resetter.reset();
        auditLogger.log("admin.reset", "system", null, "by=" + session.username());
        return Map.of("ok", true, "message", "数据已重置为种子状态");
    }
}
