package com.nextgenmanager.nextgenmanager.common.security.authorization;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

/** Access for all module roles — used on shared master data visible across all departments. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_USER'," +
        "'ROLE_PRODUCTION_ADMIN','ROLE_PRODUCTION_USER'," +
        "'ROLE_INVENTORY_ADMIN','ROLE_INVENTORY_USER'," +
        "'ROLE_PURCHASE_ADMIN','ROLE_PURCHASE_USER'," +
        "'ROLE_SALES_ADMIN','ROLE_SALES_USER')")
public @interface RequiresAllModulesAccess {
}
