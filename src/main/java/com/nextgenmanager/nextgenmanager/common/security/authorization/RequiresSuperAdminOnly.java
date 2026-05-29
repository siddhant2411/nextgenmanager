package com.nextgenmanager.nextgenmanager.common.security.authorization;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
public @interface RequiresSuperAdminOnly {
}
