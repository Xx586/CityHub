package com.cityhub.config;

import com.cityhub.common.ApiResponse;
import com.cityhub.common.ErrorCode;
import com.cityhub.security.RedisTokenAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            RedisTokenAuthenticationFilter tokenFilter,
                                            ObjectMapper objectMapper) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/user/code", "/user/login", "/swagger-ui/**", "/swagger-ui.html",
                                "/v3/api-docs/**", "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/shop/**", "/voucher/list/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/shop/**", "/voucher/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/shop/**", "/voucher/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/upload/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> writeError(
                                response, objectMapper, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) -> writeError(
                                response, objectMapper, ErrorCode.FORBIDDEN)))
                .addFilterBefore(tokenFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void writeError(HttpServletResponse response, ObjectMapper objectMapper, ErrorCode error)
            throws java.io.IOException {
        response.setStatus(error.getStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(error, error.getMessage()));
    }
}
