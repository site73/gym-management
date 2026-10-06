package com.gym.identity.internal;

import com.gym.identity.api.AuthContext;
import com.gym.identity.api.AuthException;
import com.gym.identity.api.AuthFacade;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 鉴权拦截器：校验 {@code X-Token} 并把会话写入 {@link AuthContext}。
 *
 * <p>只负责"是否登录"；"能做什么"由 {@link AuthContext} 的角色方法在各 Controller 内判定。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String TOKEN_HEADER = "X-Token";

    private final AuthFacade authFacade;

    public AuthInterceptor(AuthFacade authFacade) {
        this.authFacade = authFacade;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;   // 预检放行

        String token = request.getHeader(TOKEN_HEADER);
        if (token == null || token.isBlank()) {
            throw AuthException.unauthorized("未登录，请先登录");
        }
        AuthContext.set(authFacade.resolve(token));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                               Object handler, Exception ex) {
        AuthContext.clear();   // 防止线程复用导致的会话串号
    }
}
