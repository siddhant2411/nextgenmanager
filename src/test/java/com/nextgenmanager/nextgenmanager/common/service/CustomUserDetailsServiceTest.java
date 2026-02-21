package com.nextgenmanager.nextgenmanager.common.service;

import com.nextgenmanager.nextgenmanager.common.model.AppUser;
import com.nextgenmanager.nextgenmanager.common.model.Role;
import com.nextgenmanager.nextgenmanager.common.model.UserRoleMap;
import com.nextgenmanager.nextgenmanager.common.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void loadUserByUsername_returnsUserWithActiveAuthorities() {
        AppUser appUser = buildUser("admin", true, false);
        appUser.setUserRoleMaps(List.of(
                buildRoleMap(buildRole("ROLE_ADMIN", true, null)),
                buildRoleMap(buildRole("ROLE_INVENTORY_ADMIN", true, null))
        ));

        when(appUserRepository.findByUsernameAndDeletedDateIsNull("admin"))
                .thenReturn(Optional.of(appUser));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("admin");

        assertThat(userDetails.getUsername()).isEqualTo("admin");
        assertThat(userDetails.isEnabled()).isTrue();
        assertThat(userDetails.isAccountNonLocked()).isTrue();
        assertThat(userDetails.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_INVENTORY_ADMIN");
    }

    @Test
    void loadUserByUsername_throwsWhenNoActiveRoles() {
        AppUser appUser = buildUser("user-no-roles", true, false);
        appUser.setUserRoleMaps(List.of(
                buildRoleMap(buildRole("ROLE_USER", false, null)),
                buildRoleMap(buildRole("ROLE_ADMIN", true, new Date()))
        ));

        when(appUserRepository.findByUsernameAndDeletedDateIsNull("user-no-roles"))
                .thenReturn(Optional.of(appUser));

        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("user-no-roles"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("No active roles found");
    }

    @Test
    void loadUserByUsername_setsLockedFlagFromAppUser() {
        AppUser appUser = buildUser("locked-user", true, true);
        appUser.setUserRoleMaps(List.of(buildRoleMap(buildRole("ROLE_USER", true, null))));

        when(appUserRepository.findByUsernameAndDeletedDateIsNull("locked-user"))
                .thenReturn(Optional.of(appUser));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("locked-user");

        assertThat(userDetails.isAccountNonLocked()).isFalse();
    }

    private static AppUser buildUser(String username, boolean isActive, boolean isLocked) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash("dummy");
        user.setIsActive(isActive);
        user.setIsLocked(isLocked);
        return user;
    }

    private static UserRoleMap buildRoleMap(Role role) {
        UserRoleMap userRoleMap = new UserRoleMap();
        userRoleMap.setRole(role);
        return userRoleMap;
    }

    private static Role buildRole(String roleName, boolean isActive, Date deletedDate) {
        Role role = new Role();
        role.setRoleName(roleName);
        role.setIsActive(isActive);
        role.setDeletedDate(deletedDate);
        return role;
    }
}

