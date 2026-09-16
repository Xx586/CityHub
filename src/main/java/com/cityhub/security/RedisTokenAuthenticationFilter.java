package com.cityhub.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

@Component
public class RedisTokenAuthenticationFilter extends OncePerRequestFilter {
    public static final String TOKEN_PREFIX = "auth:token:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration tokenTtl;

    public RedisTokenAuthenticationFilter(StringRedisTemplate redisTemplate,
                                          ObjectMapper objectMapper,
                                          @Value("${cityhub.auth.token-ttl:30m}") Duration tokenTtl) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.tokenTtl = tokenTtl;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = bearerToken(request);
        if (token != null) {
            String key = TOKEN_PREFIX + token;
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                AuthUser user = objectMapper.readValue(json, AuthUser.class);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        user,
                        token,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()))
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
                redisTemplate.expire(key, tokenTtl);
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    public static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ") || header.length() <= 7) {
            return null;
        }
        return header.substring(7).trim();
    }
}

