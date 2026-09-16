package com.cityhub.security;

import com.cityhub.common.BusinessException;
import com.cityhub.common.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class UserContext {
    private UserContext() {
    }

    public static AuthUser requireUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    public static Long requireUserId() {
        return requireUser().id();
    }
}

