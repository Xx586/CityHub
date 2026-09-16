package com.cityhub.auth;

import com.cityhub.common.ApiResponse;
import com.cityhub.ratelimit.RateLimitDimension;
import com.cityhub.ratelimit.SlidingWindowRateLimit;
import com.cityhub.security.AuthUser;
import com.cityhub.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {
    private final AuthService authService;

    @PostMapping("/code")
    @SlidingWindowRateLimit(resource = "auth:code", dimension = RateLimitDimension.IP,
            key = "#request.phone", limit = 5, windowSeconds = 60)
    public ApiResponse<Void> sendCode(@Valid @RequestBody SendCodeRequest request) {
        authService.sendCode(request.phone());
        return ApiResponse.ok();
    }

    @PostMapping("/login")
    @SlidingWindowRateLimit(resource = "auth:login", dimension = RateLimitDimension.IP,
            limit = 10, windowSeconds = 60)
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        authService.logout(request);
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<AuthUser> me() {
        return ApiResponse.ok(UserContext.requireUser());
    }
}
