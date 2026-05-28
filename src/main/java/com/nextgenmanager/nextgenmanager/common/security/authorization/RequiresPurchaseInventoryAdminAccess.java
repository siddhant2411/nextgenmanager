package com.nextgenmanager.nextgenmanager.common.security.authorization;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

/** Create / post / confirm operations that require either purchase or inventory admin privileges. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN'," +
        "'ROLE_INVENTORY_ADMIN','ROLE_PURCHASE_ADMIN')")
public @interface RequiresPurchaseInventoryAdminAccess {
}
