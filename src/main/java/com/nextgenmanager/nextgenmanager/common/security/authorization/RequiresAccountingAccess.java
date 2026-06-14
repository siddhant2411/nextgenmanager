package com.nextgenmanager.nextgenmanager.common.security.authorization;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACCOUNTS_ADMIN','ACCOUNTS_USER','ACCOUNTS_HEAD')")
public @interface RequiresAccountingAccess {
}
