package com.nextgenmanager.nextgenmanager.common.service;

import com.nextgenmanager.nextgenmanager.common.dto.auth.AuthCreateUserRequest;
import com.nextgenmanager.nextgenmanager.common.dto.auth.AuthCreateUserResponse;
import com.nextgenmanager.nextgenmanager.common.dto.auth.AuthUserListItemResponse;
import com.nextgenmanager.nextgenmanager.common.dto.auth.AuthUpdateUserRolesRequest;
import com.nextgenmanager.nextgenmanager.common.dto.auth.AuthUpdateUserStatusRequest;
import com.nextgenmanager.nextgenmanager.common.model.AppUser;
import com.nextgenmanager.nextgenmanager.common.model.Role;
import com.nextgenmanager.nextgenmanager.common.model.UserRoleMap;
import com.nextgenmanager.nextgenmanager.common.repository.AppUserRepository;
import com.nextgenmanager.nextgenmanager.common.repository.RoleRepository;
import com.nextgenmanager.nextgenmanager.common.repository.UserRoleMapRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthUserManagementService {
    private static final Logger logger = LoggerFactory.getLogger(AuthUserManagementService.class);
    private static final String ROLE_SUPER_ADMIN = "ROLE_SUPER_ADMIN";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String DEFAULT_SUPER_ADMIN_USERNAME = "admin";

    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;
    private final UserRoleMapRepository userRoleMapRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    @Transactional(readOnly = true)
    public List<AuthUserListItemResponse> listUsers(String actor) {
        logger.info("User list requested by {}", actor);

        List<AuthUserListItemResponse> users = appUserRepository.findAllByDeletedDateIsNullOrderByUsernameAsc()
                .stream()
                .map(this::toUserListItem)
                .toList();

        logger.debug("Returning {} user(s) for actor {}", users.size(), actor);
        return users;
    }

    @Transactional
    public AuthCreateUserResponse createUser(AuthCreateUserRequest request, String actor) {
        if (request == null || isBlank(request.username()) || isBlank(request.password())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username and password are required");
        }

        String username = request.username().trim();
        String email = normalizeEmail(request.email());
        List<String> requestedRoles = request.roleNames();

        if (requestedRoles == null || requestedRoles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "at least one role is required");
        }

        logger.info("Create user requested by {} for username {}", actor, username);

        if (appUserRepository.findByUsernameAndDeletedDateIsNull(username).isPresent()) {
            logger.warn("Create user rejected: username already exists ({})", username);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "username already exists");
        }
        if (email != null && appUserRepository.findByEmailAndDeletedDateIsNull(email).isPresent()) {
            logger.warn("Create user rejected: email already exists ({})", email);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already exists");
        }

        Set<String> roleNames = requestedRoles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (roleNames.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "at least one valid role is required");
        }
        if (roleNames.contains("ROLE_SUPER_ADMIN")) {
            logger.warn("Create user rejected: ROLE_SUPER_ADMIN assignment is not allowed for username {}", username);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ROLE_SUPER_ADMIN assignment is not allowed");
        }

        List<Role> roles = roleNames.stream()
                .map(roleRepository::findByRoleName)
                .toList();

        boolean hasMissingRole = roles.stream().anyMatch(Objects::isNull);
        if (hasMissingRole) {
            logger.warn("Create user rejected: one or more roles do not exist for username {}", username);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "one or more roles do not exist");
        }

        boolean hasInactiveRole = roles.stream()
                .anyMatch(role -> role.getDeletedDate() != null || !Boolean.TRUE.equals(role.getIsActive()));
        if (hasInactiveRole) {
            logger.warn("Create user rejected: one or more roles are inactive for username {}", username);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "one or more roles are inactive");
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEmail(email);
        user.setIsActive(true);
        user.setIsLocked(false);
        user.setCreatedBy(actor);
        user.setUpdatedBy(actor);
        AppUser savedUser = appUserRepository.save(user);

        for (Role role : roles) {
            UserRoleMap userRoleMap = new UserRoleMap();
            userRoleMap.setAppUser(savedUser);
            userRoleMap.setRole(role);
            userRoleMap.setCreatedBy(actor);
            userRoleMap.setUpdatedBy(actor);
            userRoleMapRepository.save(userRoleMap);
        }

        List<String> assignedRoles = roles.stream().map(Role::getRoleName).toList();
        logger.info("User {} created by {} with {} role(s)", username, actor, assignedRoles.size());
        logger.debug("Assigned roles for user {}: {}", username, assignedRoles);

        return new AuthCreateUserResponse(
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getEmail(),
                savedUser.getIsActive(),
                savedUser.getIsLocked(),
                assignedRoles
        );
    }

    @Transactional
    public AuthCreateUserResponse updateUserStatus(
            Long userId,
            AuthUpdateUserStatusRequest request,
            String actor,
            boolean isSuperAdmin
    ) {
        if (request == null || (request.isActive() == null && request.isLocked() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "isActive or isLocked is required");
        }

        AppUser user = appUserRepository.findByIdAndDeletedDateIsNull(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));

        enforceSuperAdminMutationPolicy(user, isSuperAdmin);

        if (request.isActive() != null) {
            user.setIsActive(request.isActive());
        }
        if (request.isLocked() != null) {
            user.setIsLocked(request.isLocked());
        }
        user.setUpdatedBy(actor);
        appUserRepository.save(user);
        if (Boolean.TRUE.equals(user.getIsLocked()) || Boolean.FALSE.equals(user.getIsActive())) {
            refreshTokenService.revokeAllForUser(user.getId(), actor);
        }

        logger.info("User status updated by {} for username {} (active={}, locked={})",
                actor, user.getUsername(), user.getIsActive(), user.getIsLocked());
        return toUserResponse(user);
    }

    @Transactional
    public AuthCreateUserResponse updateUserRoles(
            Long userId,
            AuthUpdateUserRolesRequest request,
            String actor,
            boolean isSuperAdmin
    ) {
        if (request == null || request.roleNames() == null || request.roleNames().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "at least one role is required");
        }

        AppUser user = appUserRepository.findByIdAndDeletedDateIsNull(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));

        Set<String> targetRoleNames = request.roleNames().stream()
                .filter(role -> role != null && !role.isBlank())
                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (targetRoleNames.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "at least one valid role is required");
        }
        if (targetRoleNames.contains(ROLE_SUPER_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ROLE_SUPER_ADMIN assignment is not allowed");
        }

        Set<String> currentRoleNames = getCurrentRoleNames(user);
        boolean adminRoleChange = currentRoleNames.contains(ROLE_ADMIN) != targetRoleNames.contains(ROLE_ADMIN);
        if (adminRoleChange && !isSuperAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ROLE_SUPER_ADMIN can change ROLE_ADMIN assignment");
        }

        enforceSuperAdminMutationPolicy(user, isSuperAdmin);

        List<Role> roles = targetRoleNames.stream()
                .map(roleRepository::findByRoleName)
                .toList();

        boolean hasMissingRole = roles.stream().anyMatch(Objects::isNull);
        if (hasMissingRole) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "one or more roles do not exist");
        }

        boolean hasInactiveRole = roles.stream()
                .anyMatch(role -> role.getDeletedDate() != null || !Boolean.TRUE.equals(role.getIsActive()));
        if (hasInactiveRole) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "one or more roles are inactive");
        }

        userRoleMapRepository.deleteByAppUser_Id(user.getId());
        for (Role role : roles) {
            UserRoleMap userRoleMap = new UserRoleMap();
            userRoleMap.setAppUser(user);
            userRoleMap.setRole(role);
            userRoleMap.setCreatedBy(actor);
            userRoleMap.setUpdatedBy(actor);
            userRoleMapRepository.save(userRoleMap);
        }

        user.setUpdatedBy(actor);
        appUserRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId(), actor);

        AppUser refreshed = appUserRepository.findByIdAndDeletedDateIsNull(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
        logger.info("User roles updated by {} for username {} -> {}", actor, refreshed.getUsername(), targetRoleNames);
        return toUserResponse(refreshed);
    }

    @Transactional
    public void softDeleteUser(Long userId, String actor, boolean isSuperAdmin) {
        AppUser user = appUserRepository.findByIdAndDeletedDateIsNull(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));

        enforceSuperAdminMutationPolicy(user, isSuperAdmin);

        user.setDeletedDate(new java.util.Date());
        user.setUpdatedBy(actor);
        appUserRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId(), actor);
        logger.warn("User soft-deleted by {}: {}", actor, user.getUsername());
    }

    @Transactional
    public void adminResetPassword(Long userId, String temporaryPassword, String actor, boolean isSuperAdmin) {
        if (isBlank(temporaryPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "temporaryPassword is required");
        }
        AppUser user = appUserRepository.findByIdAndDeletedDateIsNull(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
        enforceSuperAdminMutationPolicy(user, isSuperAdmin);

        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setUpdatedBy(actor);
        user.setIsLocked(false);
        appUserRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId(), actor);
        logger.warn("Temporary password reset by {} for username {}", actor, user.getUsername());
    }

    @Transactional
    public void changeOwnPassword(String username, String currentPassword, String newPassword) {
        if (isBlank(currentPassword) || isBlank(newPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currentPassword and newPassword are required");
        }

        AppUser user = appUserRepository.findByUsernameAndDeletedDateIsNull(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currentPassword is invalid");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "newPassword must be different from current password");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedBy(username);
        appUserRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId(), username);
        logger.info("Password changed by user {}", username);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String normalized = email.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private AuthUserListItemResponse toUserListItem(AppUser user) {
        List<String> roles = user.getUserRoleMaps() == null
                ? List.of()
                : user.getUserRoleMaps().stream()
                .map(UserRoleMap::getRole)
                .filter(Objects::nonNull)
                .filter(role -> role.getDeletedDate() == null)
                .filter(role -> Boolean.TRUE.equals(role.getIsActive()))
                .map(Role::getRoleName)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(roleName -> !roleName.isBlank())
                .distinct()
                .toList();

        return new AuthUserListItemResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getIsActive(),
                user.getIsLocked(),
                user.getLastLoginDate(),
                user.getCreationDate(),
                roles
        );
    }

    private AuthCreateUserResponse toUserResponse(AppUser user) {
        return new AuthCreateUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getIsActive(),
                user.getIsLocked(),
                getCurrentRoleNames(user).stream().toList()
        );
    }

    private Set<String> getCurrentRoleNames(AppUser user) {
        if (user.getUserRoleMaps() == null) {
            return Set.of();
        }
        return user.getUserRoleMaps().stream()
                .map(UserRoleMap::getRole)
                .filter(Objects::nonNull)
                .filter(role -> role.getDeletedDate() == null)
                .filter(role -> Boolean.TRUE.equals(role.getIsActive()))
                .map(Role::getRoleName)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(roleName -> !roleName.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void enforceSuperAdminMutationPolicy(AppUser user, boolean isSuperAdminActor) {
        boolean targetIsDefaultSuperAdminUser = DEFAULT_SUPER_ADMIN_USERNAME.equalsIgnoreCase(user.getUsername());
        boolean targetHasSuperAdminRole = getCurrentRoleNames(user).contains(ROLE_SUPER_ADMIN);
        if ((targetIsDefaultSuperAdminUser || targetHasSuperAdminRole) && !isSuperAdminActor) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ROLE_SUPER_ADMIN can modify super-admin user");
        }
    }
}
