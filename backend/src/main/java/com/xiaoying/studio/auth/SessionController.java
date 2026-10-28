package com.xiaoying.studio.auth;

import com.xiaoying.studio.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 轻量身份：输入昵称即登录（不存在则创建），签发不透明令牌。
 * 不做密码，演示环境下用于区分“谁的任务”，见 README「用户身份」。
 */
@RestController
public class SessionController {

    public record LoginRequest(
            @NotBlank @Pattern(regexp = "^[\\p{L}\\p{N}_-]{2,20}$", message = "需为 2-20 位字母、数字、中文、下划线或连字符")
            String nickname) {
    }

    public record SessionResponse(String token, AuthUser user) {
    }

    private final UserMapper userMapper;

    public SessionController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @PostMapping("/api/session")
    public SessionResponse login(@Valid @RequestBody LoginRequest req) {
        String nickname = req.nickname().trim();
        userMapper.insertIgnore(nickname);
        AuthUser user = userMapper.findByNickname(nickname);
        if (user == null) {
            throw ApiException.badRequest("LOGIN_FAILED", "登录失败");
        }
        String token = UUID.randomUUID().toString();
        userMapper.insertToken(token, user.id());
        return new SessionResponse(token, user);
    }

    @GetMapping("/api/me")
    public AuthUser me(@RequestAttribute(AuthInterceptor.ATTR) AuthUser user) {
        return user;
    }

    @DeleteMapping("/api/session")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String token = AuthInterceptor.extractToken(request);
        if (token != null) {
            userMapper.deleteToken(token);
        }
        return ResponseEntity.noContent().build();
    }
}
