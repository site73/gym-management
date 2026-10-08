package com.gym.identity.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/** 登录/退出接口。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthFacade authFacade;

    public AuthController(AuthFacade authFacade) {
        this.authFacade = authFacade;
    }

    public record LoginRequest(String username, String password) {}

    /** 会员自助注册入参 */
    public record RegisterRequest(String username, String password, String name, String phone) {}

    public record LoginResponse(String token, LocalDateTime expiresAt, AuthSession.UserInfo user) {}

    /**
     * 会员自助注册（无需令牌）：注册成功即登录，直接进入会员端。
     *
     * <p>一次事务内创建登录账号与会员档案，并绑定 {@code member.user_id}。
     */
    @PostMapping("/register")
    public LoginResponse register(@RequestBody RegisterRequest req) {
        AuthSession s = authFacade.register(req.username(), req.password(), req.name(), req.phone());
        return new LoginResponse(s.token(), s.expiresAt(), s.toUserInfo());
    }

    /** 登录（无需令牌） */
    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest req) {
        AuthSession s = authFacade.login(req.username(), req.password());
        return new LoginResponse(s.token(), s.expiresAt(), s.toUserInfo());
    }

    /** 当前登录用户 */
    @GetMapping("/me")
    public AuthSession.UserInfo me() {
        return AuthContext.current().toUserInfo();
    }

    /** 退出登录 */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(@RequestHeader(value = "X-Token", required = false) String token) {
        authFacade.logout(token);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
