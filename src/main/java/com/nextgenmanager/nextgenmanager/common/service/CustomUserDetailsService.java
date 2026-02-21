package com.nextgenmanager.nextgenmanager.common.service;

import com.nextgenmanager.nextgenmanager.common.model.AppUser;
import com.nextgenmanager.nextgenmanager.common.model.Role;
import com.nextgenmanager.nextgenmanager.common.model.UserRoleMap;
import com.nextgenmanager.nextgenmanager.common.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private static final Logger logger = LoggerFactory.getLogger(CustomUserDetailsService.class);

    private final AppUserRepository appUserRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = appUserRepository.findByUsernameAndDeletedDateIsNull(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        List<GrantedAuthority> authorities = user.getUserRoleMaps() == null
                ? Collections.emptyList()
                : user.getUserRoleMaps().stream()
                .map(UserRoleMap::getRole)
                .filter(Objects::nonNull)
                .filter(role -> Boolean.TRUE.equals(role.getIsActive()))
                .filter(role -> role.getDeletedDate() == null)
                .map(Role::getRoleName)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .distinct()
                .map(name -> (GrantedAuthority) new SimpleGrantedAuthority(name))
                .toList();

        if (authorities.isEmpty()) {
            logger.warn("Authentication rejected for user {} due to no active roles", username);
            throw new UsernameNotFoundException("No active roles found for user: " + username);
        }

        logger.debug("Loaded security principal for user {} with {} authorities", username, authorities.size());

        return User.builder()
                .username(user.getUsername())
                .password(user.getPasswordHash())
                .disabled(!Boolean.TRUE.equals(user.getIsActive()))
                .accountLocked(Boolean.TRUE.equals(user.getIsLocked()))
                .authorities(authorities)
                .build();
    }

}
