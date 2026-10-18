package com.nextgenmanager.nextgenmanager.common.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** The signed-in user's name, or null when the work was not started by a person (a scheduled job, a test). */
public final class CurrentUser {
    private CurrentUser() {}

    public static String nameOrNull() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) return null;
            return auth.getName();
        } catch (Exception e) {
            return null;
        }
    }
}
