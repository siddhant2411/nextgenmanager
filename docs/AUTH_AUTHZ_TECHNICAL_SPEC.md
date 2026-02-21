# NextGen Manager Auth and Authz Technical Specification

Document version: 1.2  
Last updated: 2026-02-18  
Audience: backend engineers, frontend engineers, QA, DevOps, documentation agents

## 1. Purpose and Scope
This document defines the current authentication and authorization design for the NextGen Manager backend.

It covers:
- JWT authentication model
- access and refresh token behavior
- refresh token persistence and revocation model
- role catalog and role semantics
- endpoint-level authorization rules
- security error contract (`401` and `403`)
- operational configuration keys

It is written so another AI agent can transform it into customer-facing public documentation with minimal additional interpretation.

## 2. System Components
Core backend components:
- `src/main/java/com/nextgenmanager/nextgenmanager/config/SecurityConfig.java`
- `src/main/java/com/nextgenmanager/nextgenmanager/common/security/JwtService.java`
- `src/main/java/com/nextgenmanager/nextgenmanager/common/security/JwtAuthenticationFilter.java`
- `src/main/java/com/nextgenmanager/nextgenmanager/common/security/RestAuthenticationEntryPoint.java`
- `src/main/java/com/nextgenmanager/nextgenmanager/common/security/RestAccessDeniedHandler.java`
- `src/main/java/com/nextgenmanager/nextgenmanager/common/service/CustomUserDetailsService.java`
- `src/main/java/com/nextgenmanager/nextgenmanager/common/controller/AuthController.java`
- `src/main/java/com/nextgenmanager/nextgenmanager/common/service/AuthUserManagementService.java`
- `src/main/java/com/nextgenmanager/nextgenmanager/common/service/AuthRoleManagementService.java`
- `src/main/java/com/nextgenmanager/nextgenmanager/common/service/RefreshTokenService.java`
- `src/main/java/com/nextgenmanager/nextgenmanager/common/model/RefreshToken.java`

## 3. Authentication Architecture
### 3.1 Request authentication flow
1. Client sends `Authorization: Bearer <access_token>`.
2. `JwtAuthenticationFilter` extracts and validates token.
3. On valid token, security context is populated with user authorities.
4. On invalid token, context is cleared and request continues to Spring Security decision phase.
5. If endpoint requires auth and user is unauthenticated, `401` is returned by `RestAuthenticationEntryPoint`.

### 3.2 Public vs protected endpoints
Public endpoints currently permitted without token:
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `/swagger-ui/**`
- `/v3/api-docs/**`
- `/swagger-resources/**`
- `/webjars/**`
- `/error`

All other endpoints require authentication by default (`anyRequest().authenticated()`).

## 4. JWT Token Model
### 4.1 Algorithms and claims
- Signing algorithm: `HS512`
- Token claims include:
  - standard: `sub`, `iat`, `exp`, `iss`, `aud`
  - custom: `tokenType` (`access` or `refresh`)
  - custom: `roles` (included in access token generation)

### 4.2 Validation rules
Access token is valid only when:
- signature is valid
- token not expired
- `tokenType == access`
- issuer matches configured issuer
- audience includes configured audience
- username in token equals currently loaded user

Refresh token is valid only when:
- signature is valid
- token not expired
- `tokenType == refresh`
- issuer matches configured issuer
- audience includes configured audience
- username in token equals currently loaded user

### 4.3 Expiry policy
- access token expiry: 15 minutes
- refresh token expiry: 7 days

### 4.4 Refresh token persistence and rotation
- Refresh tokens are persisted in `refreshtoken` table.
- `POST /api/auth/login` issues and stores refresh token.
- `POST /api/auth/refresh` validates JWT + stored token, revokes old token, issues new token.
- Replay of an old refresh token fails because revoked token is not accepted.
- Sensitive account changes revoke active refresh tokens:
  - lock/deactivate user
  - role update
  - soft delete user
  - admin password reset
  - self password change

## 5. Configuration Keys
Defined in `src/main/resources/application.properties`:

- `security.jwt.secret`
- `security.jwt.algorithm` (default `HS512`)
- `security.jwt.accessExpirationMillis` (default `900000`)
- `security.jwt.refreshExpirationMillis` (default `604800000`)
- `security.jwt.issuer` (default `https://auth.erp.nextgenmanager.com`)
- `security.jwt.audience` (default `erp-backend`)

Related CORS/security:
- `frontend.url`

Secret requirement:
- For `HS512`, `security.jwt.secret` must be at least 64 characters (512 bits).
- Default secret is rejected outside `local/dev/test` profiles.

## 6. Auth API Contracts
Base path: `/api/auth`

### 6.1 Login
Endpoint:
- `POST /api/auth/login`

Request body:
```json
{
  "username": "admin",
  "password": "Admin@123"
}
```

Success response (`200`):
```json
{
  "accessToken": "string",
  "refreshToken": "string",
  "tokenType": "Bearer",
  "accessTokenExpiresIn": 900,
  "refreshTokenExpiresIn": 604800,
  "username": "admin",
  "roles": ["ROLE_ADMIN"]
}
```

Error responses:
- `400` when username/password missing
- `401` when credentials invalid

### 6.2 Refresh token
Endpoint:
- `POST /api/auth/refresh`

Request body:
```json
{
  "refreshToken": "string"
}
```

Success response (`200`): same schema as login.

Error responses:
- `400` when `refreshToken` missing
- `401` when refresh token invalid/expired/mismatched

### 6.2.1 Logout
Endpoint:
- `POST /api/auth/logout`

Request body:
```json
{
  "refreshToken": "string"
}
```

Behavior:
- revokes provided refresh token
- returns `204 No Content`

### 6.3 Current user
Endpoint:
- `GET /api/auth/me`

Headers:
- `Authorization: Bearer <access_token>`

Success response (`200`):
```json
{
  "username": "admin",
  "roles": ["ROLE_ADMIN", "ROLE_USER"]
}
```

### 6.4 Create user
Endpoint:
- `POST /api/auth/users`

Authorization:
- `ROLE_SUPER_ADMIN` or `ROLE_ADMIN`

Business rule:
- assigning `ROLE_SUPER_ADMIN` through this endpoint is blocked

Request body:
```json
{
  "username": "john",
  "password": "StrongPassword123",
  "email": "john@example.com",
  "roleNames": ["ROLE_USER", "ROLE_SALES_USER"]
}
```

Success response (`201`):
```json
{
  "id": 101,
  "username": "john",
  "email": "john@example.com",
  "isActive": true,
  "isLocked": false,
  "roles": ["ROLE_USER", "ROLE_SALES_USER"]
}
```

Error responses:
- `400` invalid payload/roles
- `409` duplicate username or email

### 6.5 List users
Endpoint:
- `GET /api/auth/users`

Authorization:
- `ROLE_SUPER_ADMIN` or `ROLE_ADMIN`

Success response (`200`):
```json
[
  {
    "id": 1,
    "username": "admin",
    "email": "admin@nextgen.local",
    "isActive": true,
    "isLocked": false,
    "lastLoginDate": "2026-02-18T12:00:00Z",
    "creationDate": "2026-02-10T08:00:00Z",
    "roles": ["ROLE_SUPER_ADMIN", "ROLE_ADMIN"]
  }
]
```

### 6.6 Update user status (lock/unlock, activate/deactivate)
Endpoint:
- `PATCH /api/auth/users/{id}/status`

Authorization:
- `ROLE_SUPER_ADMIN` or `ROLE_ADMIN`

Request body:
```json
{
  "isActive": true,
  "isLocked": false
}
```

Business rules:
- only `ROLE_SUPER_ADMIN` can mutate super-admin user

### 6.7 Update user roles
Endpoint:
- `PUT /api/auth/users/{id}/roles`

Authorization:
- `ROLE_SUPER_ADMIN` or `ROLE_ADMIN`

Request body:
```json
{
  "roleNames": ["ROLE_USER", "ROLE_SALES_ADMIN"]
}
```

Business rules:
- assigning `ROLE_SUPER_ADMIN` is blocked
- only `ROLE_SUPER_ADMIN` can change `ROLE_ADMIN` assignment
- only `ROLE_SUPER_ADMIN` can mutate super-admin user

### 6.8 Soft delete user
Endpoint:
- `DELETE /api/auth/users/{id}`

Authorization:
- `ROLE_SUPER_ADMIN` or `ROLE_ADMIN`

Business rules:
- soft delete only (`deletedDate` set)
- only `ROLE_SUPER_ADMIN` can soft-delete super-admin user

### 6.9 Admin temporary password reset
Endpoint:
- `PATCH /api/auth/users/{id}/reset-password`

Authorization:
- `ROLE_SUPER_ADMIN` or `ROLE_ADMIN`

Request body:
```json
{
  "temporaryPassword": "Temp#12345"
}
```

Behavior:
- sets provided temporary password (bcrypt hash)
- unlocks user
- revokes all refresh tokens for target user
- super-admin mutation policy enforced

### 6.10 Self password change
Endpoint:
- `PATCH /api/auth/me/password`

Authorization:
- authenticated user

Request body:
```json
{
  "currentPassword": "Current#123",
  "newPassword": "New#12345"
}
```

Behavior:
- validates current password against stored hash
- requires new password to differ from current
- updates password hash
- revokes all refresh tokens for user

### 6.11 Role management APIs
Endpoints:
- `GET /api/auth/roles` (`ROLE_SUPER_ADMIN` or `ROLE_ADMIN`)
- `POST /api/auth/roles` (`ROLE_SUPER_ADMIN`)
- `PUT /api/auth/roles/{id}` (`ROLE_SUPER_ADMIN`)
- `DELETE /api/auth/roles/{id}` (`ROLE_SUPER_ADMIN`)

Safeguards:
- system roles cannot be modified/deleted
- `ROLE_SUPER_ADMIN` cannot be created via API
- role deletion blocked if role is currently assigned

## 7. Authorization Model (RBAC)
## 7.1 Seeded roles
Current seeded role names:
- `ROLE_SUPER_ADMIN`
- `ROLE_ADMIN`
- `ROLE_USER`
- `ROLE_PRODUCTION_ADMIN`
- `ROLE_PRODUCTION_USER`
- `ROLE_INVENTORY_ADMIN`
- `ROLE_INVENTORY_USER`
- `ROLE_PURCHASE_ADMIN`
- `ROLE_PURCHASE_USER`
- `ROLE_SALES_ADMIN`
- `ROLE_SALES_USER`

Seed source:
- `src/main/resources/db/migration/V46__seed_auth_data.sql`

## 7.2 Role semantics
- `ROLE_SUPER_ADMIN`: top-level authority, intended single user (`admin`).
- `ROLE_ADMIN`: system admin, multiple users allowed.
- `ROLE_USER`: broad baseline module access.
- module admin roles: elevated control within module (approvals, destructive actions, status/version changes).
- module user roles: standard module operations.

## 7.3 Controller-level baseline access
Class-level `@PreAuthorize` controls baseline access by module.

- Sales module controllers allow:
  - `ROLE_SUPER_ADMIN`, `ROLE_ADMIN`, `ROLE_USER`, `ROLE_SALES_ADMIN`, `ROLE_SALES_USER`
- Inventory module controllers allow:
  - `ROLE_SUPER_ADMIN`, `ROLE_ADMIN`, `ROLE_USER`, `ROLE_INVENTORY_ADMIN`, `ROLE_INVENTORY_USER`
- Production and BOM controllers allow:
  - `ROLE_SUPER_ADMIN`, `ROLE_ADMIN`, `ROLE_USER`, `ROLE_PRODUCTION_ADMIN`, `ROLE_PRODUCTION_USER`
- Item code mapping controller baseline allows wide cross-module roles.

## 7.4 Method-level restricted actions (admin-only)
The following endpoints are additionally restricted with method-level `@PreAuthorize`:

- Inventory item write operations:
  - `POST /api/inventory_item/add`
  - `PUT /api/inventory_item/{id}`
  - `DELETE /api/inventory_item/{id}`
  - `POST /api/inventory_item/{id}/upload`
  - allowed: `ROLE_SUPER_ADMIN`, `ROLE_ADMIN`, `ROLE_INVENTORY_ADMIN`

- Inventory approval and sensitive operations:
  - `PUT /api/inventory/requests/approve`
  - `PUT /api/inventory/requests/reject`
  - `DELETE /api/inventory/{id}`
  - `PUT /api/inventory/{id}`
  - `POST /api/inventory/add`
  - `POST /api/inventory/add-instances`
  - `PUT /api/inventory/inventory-procurement/{id}/status`
  - `POST /api/inventory/procurement/{procurementOrderId}/add`
  - `PUT /api/inventory/inventory-procurement-orders/{orderId}/complete`
  - allowed: `ROLE_SUPER_ADMIN`, `ROLE_ADMIN`, `ROLE_INVENTORY_ADMIN`

- Production critical operations:
  - `PATCH /api/production/work-order/material/issue`
  - `PATCH /api/production/work-order/{id}/cancel`
  - `DELETE /api/production/work-order/{id}`
  - allowed: `ROLE_SUPER_ADMIN`, `ROLE_ADMIN`, `ROLE_PRODUCTION_ADMIN`

- Routing lifecycle/version operations:
  - `POST /api/manufacturing/routing/bom/{bomId}`
  - `PUT /api/manufacturing/routing/{routingId}/operations`
  - `POST /api/manufacturing/routing/{routingId}/approve`
  - `POST /api/manufacturing/routing/{routingId}/activate`
  - `POST /api/manufacturing/routing/{routingId}/obsolete`
  - allowed: `ROLE_SUPER_ADMIN`, `ROLE_ADMIN`, `ROLE_PRODUCTION_ADMIN`

- BOM status/version transitions:
  - `POST /api/bom/changeStatus/{bomId}`
  - allowed: `ROLE_SUPER_ADMIN`, `ROLE_ADMIN`, `ROLE_PRODUCTION_ADMIN`

- Item code mapping write operations:
  - `POST /api/item-code-mapping`
  - `PUT /api/item-code-mapping/{id}`
  - `DELETE /api/item-code-mapping/{id}`
  - allowed admin roles:
    - `ROLE_SUPER_ADMIN`, `ROLE_ADMIN`,
    - `ROLE_PRODUCTION_ADMIN`, `ROLE_INVENTORY_ADMIN`, `ROLE_PURCHASE_ADMIN`, `ROLE_SALES_ADMIN`

## 8. Security Error Contract
Unauthorized (`401`) and forbidden (`403`) responses are standardized JSON.

401 response shape:
```json
{
  "timestamp": "2026-02-18T10:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Authentication is required to access this resource",
  "path": "/api/some-protected-path"
}
```

403 response shape:
```json
{
  "timestamp": "2026-02-18T10:00:00Z",
  "status": 403,
  "error": "Forbidden",
  "message": "You do not have permission to access this resource",
  "path": "/api/some-admin-path"
}
```

## 9. Data Model Notes
Primary auth tables:
- `appuser`
- `role`
- `userrolemap`
- `refreshtoken`

Key behavior:
- users are soft-deletable (`deletedDate`)
- roles used for auth are active and non-deleted
- authentication rejects users with no active roles

## 10. Logging and Audit Behavior
Current logging includes:
- login success/failure
- token generation debug logs
- user creation/list access logs
- JWT authentication failures

Sensitive data handling:
- password hashes are stored, plaintext passwords are not logged
- tokens are not intentionally logged in auth paths

## 11. Known Business Rules and Constraints
- `/api/auth/me` remains protected.
- `ROLE_SUPER_ADMIN` is intended to remain unique and not assignable via standard create-user API.
- `ROLE_ADMIN` can exist for multiple users.
- user-management APIs are admin-only (`ROLE_SUPER_ADMIN` or `ROLE_ADMIN`).
- only `ROLE_SUPER_ADMIN` can change `ROLE_ADMIN` role assignment.
- only `ROLE_SUPER_ADMIN` can mutate super-admin user.

## 12. Non-goals and Pending Work
Not yet fully implemented:
- integration test coverage for new user-management, role-management, and refresh-token persistence flows
- dedicated endpoint for explicit revoke-all-sessions by admin
- dedicated public OpenAPI auth section

## 13. QA Validation Checklist
- Login returns both access and refresh token.
- Refresh endpoint returns new token pair for valid refresh token.
- `/api/auth/me` fails with `401` when no token.
- admin-only endpoints fail with `403` for non-admin tokens.
- create user fails on `ROLE_SUPER_ADMIN` assignment attempt.
- `PATCH /api/auth/users/{id}/status` enforces super-admin mutation policy.
- `PUT /api/auth/users/{id}/roles` blocks admin-role changes by non-super-admin.
- `DELETE /api/auth/users/{id}` performs soft delete and respects super-admin policy.
- `PATCH /api/auth/users/{id}/reset-password` revokes target refresh tokens.
- `PATCH /api/auth/me/password` validates current password and revokes sessions.
- role management endpoints enforce system-role safeguards.
- role-restricted module actions respect method-level restrictions.

## 14. AI Handoff Block (for downstream public documentation generation)
The block below is intentionally concise and structured for machine consumption.

```yaml
product: NextGen Manager
module: auth-authz
auth_scheme: Bearer JWT
jwt:
  algorithm: HS512
  access_token_expiry: 15m
  refresh_token_expiry: 7d
  issuer: https://auth.erp.nextgenmanager.com
  audience: erp-backend
public_endpoints:
  - POST /api/auth/login
  - POST /api/auth/refresh
protected_default: true
auth_endpoints:
  - GET /api/auth/me
  - POST /api/auth/logout
  - POST /api/auth/users
  - GET /api/auth/users
  - PATCH /api/auth/users/{id}/status
  - PUT /api/auth/users/{id}/roles
  - DELETE /api/auth/users/{id}
  - PATCH /api/auth/users/{id}/reset-password
  - PATCH /api/auth/me/password
  - GET /api/auth/roles
  - POST /api/auth/roles
  - PUT /api/auth/roles/{id}
  - DELETE /api/auth/roles/{id}
roles:
  - ROLE_SUPER_ADMIN
  - ROLE_ADMIN
  - ROLE_USER
  - ROLE_PRODUCTION_ADMIN
  - ROLE_PRODUCTION_USER
  - ROLE_INVENTORY_ADMIN
  - ROLE_INVENTORY_USER
  - ROLE_PURCHASE_ADMIN
  - ROLE_PURCHASE_USER
  - ROLE_SALES_ADMIN
  - ROLE_SALES_USER
core_rules:
  - ROLE_SUPER_ADMIN is not assignable via create-user API
  - ROLE_SUPER_ADMIN mutation is allowed only to ROLE_SUPER_ADMIN
  - ROLE_ADMIN assignment changes are allowed only to ROLE_SUPER_ADMIN
  - Refresh tokens are persisted and rotated on refresh
  - Sensitive account mutations revoke active refresh tokens
  - ROLE_ADMIN can be multiple users
  - role checks are enforced by class-level and method-level PreAuthorize
error_contract:
  unauthorized:
    status: 401
    error: Unauthorized
  forbidden:
    status: 403
    error: Forbidden
```
