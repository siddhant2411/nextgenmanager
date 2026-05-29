package com.nextgenmanager.nextgenmanager.common.security.authorization;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

/** Read/write access for the purchase module (includes inventory roles as they work with receipts). */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_USER'," +
        "'ROLE_INVENTORY_ADMIN','ROLE_INVENTORY_USER'," +
        "'ROLE_PURCHASE_ADMIN','ROLE_PURCHASE_USER')")
public @interface RequiresPurchaseAccess {
}
