package com.nextgenmanager.nextgenmanager.common.service;

import com.nextgenmanager.nextgenmanager.common.dto.auth.AuthUpdateUserRolesRequest;
import com.nextgenmanager.nextgenmanager.common.model.AppUser;
import com.nextgenmanager.nextgenmanager.common.model.Role;
import com.nextgenmanager.nextgenmanager.common.model.UserRoleMap;
import com.nextgenmanager.nextgenmanager.common.repository.AppUserRepository;
import com.nextgenmanager.nextgenmanager.common.repository.RoleRepository;
import com.nextgenmanager.nextgenmanager.common.repository.UserRoleMapRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthUserManagementServiceTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserRoleMapRepository userRoleMapRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthUserManagementService service;

    @Test
    void changeOwnPassword_success_updatesAndRevokesTokens() {
        AppUser user = new AppUser();
        user.setId(10L);
        user.setUsername("john");
        user.setPasswordHash("old-hash");

        when(appUserRepository.findByUsernameAndDeletedDateIsNull("john")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current123", "old-hash")).thenReturn(true);
        when(passwordEncoder.matches("new123", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("new123")).thenReturn("new-hash");

        service.changeOwnPassword("john", "current123", "new123");

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("new-hash");
        verify(refreshTokenService).revokeAllForUser(10L, "john");
    }

    @Test
    void changeOwnPassword_invalidCurrentPassword_throws() {
        AppUser user = new AppUser();
        user.setId(10L);
        user.setUsername("john");
        user.setPasswordHash("old-hash");

        when(appUserRepository.findByUsernameAndDeletedDateIsNull("john")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changeOwnPassword("john", "wrong", "new123"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("currentPassword is invalid");

        verify(appUserRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAllForUser(any(), any());
    }

    @Test
    void adminResetPassword_success_unlocksAndRevokesTokens() {
        AppUser user = new AppUser();
        user.setId(7L);
        user.setUsername("user1");
        user.setPasswordHash("old");
        user.setIsLocked(true);

        when(appUserRepository.findByIdAndDeletedDateIsNull(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("Temp@123")).thenReturn("temp-hash");

        service.adminResetPassword(7L, "Temp@123", "admin", false);

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("temp-hash");
        assertThat(captor.getValue().getIsLocked()).isFalse();
        verify(refreshTokenService).revokeAllForUser(7L, "admin");
    }

    @Test
    void updateUserRoles_nonSuperAdminCannotChangeAdminAssignment() {
        AppUser user = new AppUser();
        user.setId(8L);
        user.setUsername("target");
        user.setUserRoleMaps(List.of(roleMap("ROLE_USER")));

        when(appUserRepository.findByIdAndDeletedDateIsNull(8L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.updateUserRoles(
                8L,
                new AuthUpdateUserRolesRequest(List.of("ROLE_ADMIN")),
                "admin",
                false
        )).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only ROLE_SUPER_ADMIN can change ROLE_ADMIN assignment");

        verify(userRoleMapRepository, never()).deleteByAppUser_Id(any());
    }

    private static UserRoleMap roleMap(String roleName) {
        Role role = new Role();
        role.setRoleName(roleName);
        role.setIsActive(true);

        UserRoleMap map = new UserRoleMap();
        map.setRole(role);
        return map;
    }
}
