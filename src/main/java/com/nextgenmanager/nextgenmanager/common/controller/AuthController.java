package com.nextgenmanager.nextgenmanager.common.controller;

import com.nextgenmanager.nextgenmanager.common.dto.auth.AuthCreateUserRequest;
import com.nextgenmanager.nextgenmanager.common.dto.auth.AuthCreateUserResponse;
import com.nextgenmanager.nextgenmanager.common.dto.auth.AuthCreateRoleRequest;
import com.nextgenmanager.nextgenmanager.common.dto.auth.AuthChangePasswordRequest;
import com.nextgenmanager.nextgenmanager.common.dto.auth.AuthLoginRequest;
import com.nextgenmanager.nextgenmanager.common.dto.auth.AuthLoginResponse;
import com.nextgenmanager.nextgenmanager.common.dto.auth.AuthLogoutRequest;
import com.nextgenmanager.nextgenmanager.common.dto.auth.AuthRefreshRequest;
import com.nextgenmanager.nextgenmanager.common.dto.auth.AuthRoleResponse;
import com.nextgenmanager.nextgenmanager.common.dto.auth.AuthUpdateRoleRequest;
import com.nextgenmanager.nextgenmanager.common.dto.auth.AuthUpdateUserRolesRequest;
import com.nextgenmanager.nextgenmanager.common.dto.auth.AuthUpdateUserStatusRequest;
import com.nextgenmanager.nextgenmanager.common.dto.auth.AuthAdminResetPasswordRequest;
import com.nextgenmanager.nextgenmanager.common.dto.auth.AuthUserListItemResponse;
import com.nextgenmanager.nextgenmanager.common.dto.auth.AuthUserResponse;
import com.nextgenmanager.nextgenmanager.common.model.AppUser;
import com.nextgenmanager.nextgenmanager.common.repository.AppUserRepository;
import com.nextgenmanager.nextgenmanager.common.security.JwtService;
import com.nextgenmanager.nextgenmanager.common.service.AuthRoleManagementService;
import com.nextgenmanager.nextgenmanager.common.service.AuthUserManagementService;
import com.nextgenmanager.nextgenmanager.common.service.CustomUserDetailsService;
import com.nextgenmanager.nextgenmanager.common.service.RefreshTokenService;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Authentication, authorization, users and roles management APIs")
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AppUserRepository appUserRepository;
    private final AuthUserManagementService authUserManagementService;
    private final AuthRoleManagementService authRoleManagementService;
    private final CustomUserDetailsService customUserDetailsService;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and issue access/refresh tokens")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    public ResponseEntity<AuthLoginResponse> login(@RequestBody AuthLoginRequest request) {
        if (request == null || request.username() == null || request.password() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username and password are required");
        }


        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username().trim(), request.password())
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        String accessToken = jwtService.generateAccessToken(Map.of("roles", roles), userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);
        AppUser appUser = appUserRepository.findByUsernameAndDeletedDateIsNull(userDetails.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
        refreshTokenService.issueToken(
                appUser,
                refreshToken,
                jwtService.extractClaim(refreshToken, Claims::getExpiration),
                userDetails.getUsername()
        );
        updateLastLoginDate(userDetails.getUsername());
        logger.info("Login successful for user: {}", userDetails.getUsername());

        AuthLoginResponse response = new AuthLoginResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtService.getAccessTokenExpirationSeconds(),
                jwtService.getRefreshTokenExpirationSeconds(),
                userDetails.getUsername(),
                roles
        );
        return ResponseEntity.ok(response);

    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token refresh successful"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Invalid or revoked refresh token")
    })
    public ResponseEntity<AuthLoginResponse> refresh(@RequestBody AuthRefreshRequest request) {
        if (request == null || request.refreshToken() == null || request.refreshToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "refreshToken is required");
        }

        try {
            String incomingRefreshToken = request.refreshToken().trim();
            var storedToken = refreshTokenService.findActiveToken(incomingRefreshToken)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

            String username = jwtService.extractUsername(incomingRefreshToken);
            UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);
            if (!jwtService.isRefreshTokenValid(incomingRefreshToken, userDetails)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
            }
            if (!storedToken.getAppUser().getUsername().equals(username)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
            }

            List<String> roles = userDetails.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .toList();

            String accessToken = jwtService.generateAccessToken(Map.of("roles", roles), userDetails);
            String refreshToken = jwtService.generateRefreshToken(userDetails);
            refreshTokenService.revokeToken(incomingRefreshToken, username);
            refreshTokenService.issueToken(
                    storedToken.getAppUser(),
                    refreshToken,
                    jwtService.extractClaim(refreshToken, Claims::getExpiration),
                    username
            );

            AuthLoginResponse response = new AuthLoginResponse(
                    accessToken,
                    refreshToken,
                    "Bearer",
                    jwtService.getAccessTokenExpirationSeconds(),
                    jwtService.getRefreshTokenExpirationSeconds(),
                    userDetails.getUsername(),
                    roles
            );
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.warn("Refresh token validation failed: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout by revoking refresh token")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "204", description = "Logout successful")
    public ResponseEntity<Void> logout(@RequestBody AuthLogoutRequest request, Authentication authentication) {
        String actor = authentication == null ? "SYSTEM" : authentication.getName();
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            refreshTokenService.revokeToken(request.refreshToken().trim(), actor);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Get currently authenticated user")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current user"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<AuthUserResponse> me(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        logger.debug("Fetching /auth/me for user: {}", authentication.getName());

        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return ResponseEntity.ok(new AuthUserResponse(authentication.getName(), roles));
    }

    @PostMapping("/users")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN')")
    @Operation(summary = "Create user (admin)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<AuthCreateUserResponse> createUser(
            @RequestBody AuthCreateUserRequest request,
            Authentication authentication
    ) {
        String actor = authentication == null ? "SYSTEM" : authentication.getName();
        AuthCreateUserResponse response = authUserManagementService.createUser(request, actor);

        return ResponseEntity
                .created(URI.create("/api/auth/users/" + response.id()))
                .header(HttpHeaders.LOCATION, "/api/auth/users/" + response.id())
                .body(response);
    }

    @GetMapping("/users")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN')")
    @Operation(summary = "List users (admin)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<AuthUserListItemResponse>> listUsers(Authentication authentication) {
        String actor = authentication == null ? "SYSTEM" : authentication.getName();
        return ResponseEntity.ok(authUserManagementService.listUsers(actor));
    }

    @PatchMapping("/users/{id}/status")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN')")
    @Operation(summary = "Update user active/locked status (admin)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<AuthCreateUserResponse> updateUserStatus(
            @PathVariable Long id,
            @RequestBody AuthUpdateUserStatusRequest request,
            Authentication authentication
    ) {
        String actor = authentication == null ? "SYSTEM" : authentication.getName();
        boolean isSuperAdmin = hasAuthority(authentication, "ROLE_SUPER_ADMIN");
        return ResponseEntity.ok(authUserManagementService.updateUserStatus(id, request, actor, isSuperAdmin));
    }

    @PutMapping("/users/{id}/roles")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN')")
    @Operation(summary = "Replace user roles")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<AuthCreateUserResponse> updateUserRoles(
            @PathVariable Long id,
            @RequestBody AuthUpdateUserRolesRequest request,
            Authentication authentication
    ) {
        String actor = authentication == null ? "SYSTEM" : authentication.getName();
        boolean isSuperAdmin = hasAuthority(authentication, "ROLE_SUPER_ADMIN");
        return ResponseEntity.ok(authUserManagementService.updateUserRoles(id, request, actor, isSuperAdmin));
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN')")
    @Operation(summary = "Soft delete user")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> softDeleteUser(
            @PathVariable Long id,
            Authentication authentication
    ) {
        String actor = authentication == null ? "SYSTEM" : authentication.getName();
        boolean isSuperAdmin = hasAuthority(authentication, "ROLE_SUPER_ADMIN");
        authUserManagementService.softDeleteUser(id, actor, isSuperAdmin);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/users/{id}/reset-password")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN')")
    @Operation(summary = "Reset user password with temporary password (admin)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> adminResetPassword(
            @PathVariable Long id,
            @RequestBody AuthAdminResetPasswordRequest request,
            Authentication authentication
    ) {
        String actor = authentication == null ? "SYSTEM" : authentication.getName();
        boolean isSuperAdmin = hasAuthority(authentication, "ROLE_SUPER_ADMIN");
        authUserManagementService.adminResetPassword(
                id,
                request == null ? null : request.temporaryPassword(),
                actor,
                isSuperAdmin
        );
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/me/password")
    @Operation(summary = "Change own password")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> changeOwnPassword(
            @RequestBody AuthChangePasswordRequest request,
            Authentication authentication
    ) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        authUserManagementService.changeOwnPassword(
                authentication.getName(),
                request == null ? null : request.currentPassword(),
                request == null ? null : request.newPassword()
        );
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN')")
    @Operation(summary = "List roles")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<AuthRoleResponse>> listRoles() {
        return ResponseEntity.ok(authRoleManagementService.listRoles());
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Create role (super admin)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<AuthRoleResponse> createRole(
            @RequestBody AuthCreateRoleRequest request,
            Authentication authentication
    ) {
        String actor = authentication == null ? "SYSTEM" : authentication.getName();
        AuthRoleResponse response = authRoleManagementService.createRole(request, actor);
        return ResponseEntity
                .created(URI.create("/api/auth/roles/" + response.id()))
                .body(response);
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Update role (super admin)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<AuthRoleResponse> updateRole(
            @PathVariable Long id,
            @RequestBody AuthUpdateRoleRequest request,
            Authentication authentication
    ) {
        String actor = authentication == null ? "SYSTEM" : authentication.getName();
        return ResponseEntity.ok(authRoleManagementService.updateRole(id, request, actor));
    }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Soft delete role (super admin)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> deleteRole(
            @PathVariable Long id,
            Authentication authentication
    ) {
        String actor = authentication == null ? "SYSTEM" : authentication.getName();
        authRoleManagementService.deleteRole(id, actor);
        return ResponseEntity.noContent().build();
    }

    private void updateLastLoginDate(String username) {
        appUserRepository.findByUsernameAndDeletedDateIsNull(username)
                .ifPresent(user -> {
                    user.setLastLoginDate(new Date());
                    user.setUpdatedBy(username);
                    appUserRepository.save(user);
                    logger.debug("Updated lastLoginDate for user: {}", username);
                });
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null
                && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> authority.equals(grantedAuthority.getAuthority()));
    }

}
