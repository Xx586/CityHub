package com.cityhub.security;

import com.cityhub.domain.enums.UserRole;

public record AuthUser(Long id, String nickName, String icon, UserRole role) {
}

