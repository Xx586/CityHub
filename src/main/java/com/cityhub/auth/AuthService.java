package com.cityhub.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cityhub.common.BusinessException;
import com.cityhub.common.ErrorCode;
import com.cityhub.domain.entity.User;
import com.cityhub.domain.enums.UserRole;
import com.cityhub.mapper.UserMapper;
import com.cityhub.security.AuthUser;
import com.cityhub.security.RedisTokenAuthenticationFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class AuthService {
    private static final String CODE_PREFIX = "auth:code:";

    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration tokenTtl;
    private final Duration codeTtl;

    public AuthService(UserMapper userMapper,
                       StringRedisTemplate redisTemplate,
                       ObjectMapper objectMapper,
                       @Value("${cityhub.auth.token-ttl:30m}") Duration tokenTtl,
                       @Value("${cityhub.auth.code-ttl:2m}") Duration codeTtl) {
        this.userMapper = userMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.tokenTtl = tokenTtl;
        this.codeTtl = codeTtl;
    }

    public void sendCode(String phone) {
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        redisTemplate.opsForValue().set(CODE_PREFIX + phone, code, codeTtl);
        log.info("DEV SMS code issued: phone={}****{}, code={}, ttl={}s",
                phone.substring(0, 3), phone.substring(7), code, codeTtl.toSeconds());
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        String key = CODE_PREFIX + request.phone();
        String cachedCode = redisTemplate.opsForValue().get(key);
        if (cachedCode == null || !cachedCode.equals(request.code())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码错误或已过期");
        }
        redisTemplate.delete(key);

        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getPhone, request.phone()));
        if (user == null) {
            user = new User();
            user.setPhone(request.phone());
            user.setNickName("CityHub用户_" + request.phone().substring(7));
            user.setIcon("");
            user.setRole(UserRole.USER);
            userMapper.insert(user);
        }
        AuthUser authUser = new AuthUser(user.getId(), user.getNickName(), user.getIcon(), user.getRole());
        String token = UUID.randomUUID().toString().replace("-", "");
        try {
            redisTemplate.opsForValue().set(RedisTokenAuthenticationFilter.TOKEN_PREFIX + token,
                    objectMapper.writeValueAsString(authUser), tokenTtl);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize login user", exception);
        }
        return new TokenResponse(token, tokenTtl.toSeconds());
    }

    public void logout(HttpServletRequest request) {
        String token = RedisTokenAuthenticationFilter.bearerToken(request);
        if (token != null) {
            redisTemplate.delete(RedisTokenAuthenticationFilter.TOKEN_PREFIX + token);
        }
    }
}

