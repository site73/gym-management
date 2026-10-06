package com.gym.shared.audit;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 审计日志查询服务（Controller 不直连仓储，符合分层约束）。 */
@Service
public class AuditQueryService {

    private final AuditLogRepository repository;

    public AuditQueryService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public record AuditRow(Long id, String action, String targetType, Long targetId,
                           String detail, LocalDateTime createdAt) {}

    /** 最近 50 条审计记录 */
    public List<AuditRow> recent() {
        return repository.findTop50ByOrderByIdDesc().stream()
                .map(a -> new AuditRow(a.getId(), a.getAction(), a.getTargetType(),
                        a.getTargetId(), a.getDetail(), a.getCreatedAt()))
                .toList();
    }
}
