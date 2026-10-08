package com.gym.identity.internal;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 配置：为 {@code /api/**} 挂上鉴权拦截器。
 * 登录接口与健康检查放行；静态资源不在拦截范围内。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                // 登录 / 注册无需令牌；支付回调由支付平台直接调用（真实环境以验签保护），也不携带前端令牌
                .excludePathPatterns("/api/auth/login", "/api/auth/register", "/api/pay/notify/**");
    }
}
