package com.gym.shared.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 审计日志（权限/状态/财务相关操作留痕）。
 * 同时输出到日志文件与 audit_log 表，满足可追溯要求。
 */
@Component
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);

    private final AuditLogRepository repository;

    public AuditLogger(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void log(String action, String targetType, Long targetId, String detail) {
        log.info("[audit] action={} target={}:{} detail={}", action, targetType, targetId, detail);
        try {
            repository.save(new AuditLogEntity(action, targetType, targetId, detail));
        } catch (Exception e) {
            // 审计失败不应阻断主流程，但必须记录
            log.warn("[audit] 落库失败：{}", e.getMessage());
        }
    }
}
