package com.gym.system.api;

import com.gym.identity.api.AuthContext;
import com.gym.shared.audit.AuditLogger;
import com.gym.system.internal.DemoDataResetter;
import com.gym.system.internal.DemoScheduleAligner;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 系统管理接口（仅管理员）。 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final DemoDataResetter resetter;
    private final DemoScheduleAligner aligner;
    private final AuditLogger auditLogger;

    public AdminController(DemoDataResetter resetter, DemoScheduleAligner aligner, AuditLogger auditLogger) {
        this.resetter = resetter;
        this.aligner = aligner;
        this.auditLogger = auditLogger;
    }

    /**
     * 重置演示数据（危险操作，仅 admin 角色）。
     *
     * <p>重置会清空场地预约与课程预约，因此随后立刻做一次「演示排课对齐」，
     * 保证重置完点开「周课表」和「场馆占用课表」仍然有内容可看。
     */
    @PostMapping("/reset")
    public Map<String, Object> reset() {
        var session = AuthContext.requireAdmin();
        resetter.reset();
        aligner.align();
        auditLogger.log("admin.reset", "system", null, "by=" + session.username());
        return Map.of("ok", true, "message", "数据已重置为种子状态");
    }
}
