package com.gym.shared.common;

import com.gym.identity.api.AuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

/**
 * 统一异常处理：把业务异常翻译成明确的 HTTP 语义，避免直接抛 500。
 *
 * <ul>
 *   <li>AuthException：未登录 → 401；权限不足 → 403</li>
 *   <li>NoResourceFoundException（如 favicon.ico 不存在）→ 404</li>
 *   <li>IllegalArgumentException（参数/对象不存在）→ 400</li>
 *   <li>IllegalStateException（状态不允许，如重复签到/重复取消）→ 409</li>
 * </ul>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    public record ErrorBody(String code, String reason) {}

    /** 静态资源缺失不应是 500（浏览器会自动请求 favicon.ico） */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorBody> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorBody("NOT_FOUND", "资源不存在"));
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ErrorBody> handleAuth(AuthException e) {
        if (e.isForbidden()) {
            log.warn("[api] 权限不足：{}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorBody("FORBIDDEN", e.getMessage()));
        }
        log.warn("[api] 未认证：{}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorBody("UNAUTHORIZED", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorBody> handleBadRequest(IllegalArgumentException e) {
        log.warn("[api] 参数/对象错误：{}", e.getMessage());
        return ResponseEntity.badRequest().body(new ErrorBody("BAD_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorBody> handleConflict(IllegalStateException e) {
        log.warn("[api] 状态冲突：{}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorBody("INVALID_STATE", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception e) {
        log.error("[api] 未预期异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("code", "INTERNAL_ERROR", "reason", String.valueOf(e.getMessage())));
    }
}
