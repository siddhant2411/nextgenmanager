package com.nextgenmanager.nextgenmanager.common.service;

import com.nextgenmanager.nextgenmanager.common.dto.auth.AuthCreateRoleRequest;
import com.nextgenmanager.nextgenmanager.common.dto.auth.AuthRoleResponse;
import com.nextgenmanager.nextgenmanager.common.dto.auth.AuthUpdateRoleRequest;
import com.nextgenmanager.nextgenmanager.common.model.Role;
import com.nextgenmanager.nextgenmanager.common.model.RoleType;
import com.nextgenmanager.nextgenmanager.common.repository.RoleRepository;
import com.nextgenmanager.nextgenmanager.common.repository.UserRoleMapRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthRoleManagementService {
    private static final Logger logger = LoggerFactory.getLogger(AuthRoleManagementService.class);
    private static final String ROLE_SUPER_ADMIN = "ROLE_SUPER_ADMIN";

    private final RoleRepository roleRepository;
    private final UserRoleMapRepository userRoleMapRepository;

    @Transactional(readOnly = true)
    public List<AuthRoleResponse> listRoles() {
        return roleRepository.findAllByDeletedDateIsNullOrderByRoleNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AuthRoleResponse createRole(AuthCreateRoleRequest request, String actor) {
        if (request == null || isBlank(request.roleName()) || isBlank(request.displayName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roleName and displayName are required");
        }
        String roleName = request.roleName().trim().toUpperCase(Locale.ROOT);
        if (ROLE_SUPER_ADMIN.equals(roleName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ROLE_SUPER_ADMIN cannot be created");
        }
        if (roleRepository.findByRoleNameAndDeletedDateIsNull(roleName).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "roleName already exists");
        }

        Role role = new Role();
        role.setRoleName(roleName);
        role.setDisplayName(request.displayName().trim());
        role.setRoleDescription(trimToNull(request.roleDescription()));
        role.setModuleName(trimToNull(request.moduleName()));
        role.setRoleType(parseRoleType(request.roleType()));
        role.setIsSystemRole(Boolean.TRUE.equals(request.isSystemRole()));
        role.setIsActive(request.isActive() == null || request.isActive());
        role.setCreatedBy(actor);
        role.setUpdatedBy(actor);
        Role saved = roleRepository.save(role);
        logger.info("Role created by {}: {}", actor, saved.getRoleName());
        return toResponse(saved);
    }

    @Transactional
    public AuthRoleResponse updateRole(Long id, AuthUpdateRoleRequest request, String actor) {
        Role role = roleRepository.findByIdAndDeletedDateIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "role not found"));
        protectSystemRole(role);

        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        if (!isBlank(request.displayName())) {
            role.setDisplayName(request.displayName().trim());
        }
        if (request.roleDescription() != null) {
            role.setRoleDescription(trimToNull(request.roleDescription()));
        }
        if (request.moduleName() != null) {
            role.setModuleName(trimToNull(request.moduleName()));
        }
        if (request.isActive() != null) {
            role.setIsActive(request.isActive());
        }
        role.setUpdatedBy(actor);
        Role saved = roleRepository.save(role);
        logger.info("Role updated by {}: {}", actor, saved.getRoleName());
        return toResponse(saved);
    }

    @Transactional
    public void deleteRole(Long id, String actor) {
        Role role = roleRepository.findByIdAndDeletedDateIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "role not found"));
        protectSystemRole(role);

        if (userRoleMapRepository.existsByRole_Id(role.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "role is assigned to users and cannot be deleted");
        }

        role.setDeletedDate(new Date());
        role.setIsActive(false);
        role.setUpdatedBy(actor);
        roleRepository.save(role);
        logger.warn("Role soft-deleted by {}: {}", actor, role.getRoleName());
    }

    private AuthRoleResponse toResponse(Role role) {
        return new AuthRoleResponse(
                role.getId(),
                role.getRoleName(),
                role.getDisplayName(),
                role.getRoleDescription(),
                role.getModuleName(),
                role.getRoleType() == null ? null : role.getRoleType().name(),
                role.getIsSystemRole(),
                role.getIsActive()
        );
    }

    private RoleType parseRoleType(String value) {
        if (isBlank(value)) {
            return RoleType.CUSTOM;
        }
        try {
            return RoleType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid roleType");
        }
    }

    private void protectSystemRole(Role role) {
        if (Boolean.TRUE.equals(role.getIsSystemRole()) || ROLE_SUPER_ADMIN.equals(role.getRoleName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "system role cannot be modified");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
