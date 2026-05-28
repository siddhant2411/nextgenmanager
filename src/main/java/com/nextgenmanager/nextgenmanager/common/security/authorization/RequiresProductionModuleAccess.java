package com.nextgenmanager.nextgenmanager.common.security.authorization;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

/** Access restricted to production team only (no generic USER); used for dashboards and templates. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN'," +
        "'ROLE_PRODUCTION_ADMIN','ROLE_PRODUCTION_USER')")
public @interface RequiresProductionModuleAccess {
}
