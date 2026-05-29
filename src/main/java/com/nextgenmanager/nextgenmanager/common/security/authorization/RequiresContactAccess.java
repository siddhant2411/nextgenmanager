package com.nextgenmanager.nextgenmanager.common.security.authorization;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

/** Access to contacts (vendors/customers) — needed by both sales and purchase teams. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_USER'," +
        "'ROLE_SALES_ADMIN','ROLE_SALES_USER'," +
        "'ROLE_PURCHASE_ADMIN','ROLE_PURCHASE_USER')")
public @interface RequiresContactAccess {
}
