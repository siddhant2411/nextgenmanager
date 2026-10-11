package com.nextgenmanager.nextgenmanager.dashboard.controller;

import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresAuthenticated;
import com.nextgenmanager.nextgenmanager.dashboard.dto.PendingApprovalDto;
import com.nextgenmanager.nextgenmanager.dashboard.service.MyApprovalsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Home-page reads that belong to no single module. Module figures stay on their own controllers. */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final MyApprovalsService myApprovalsService;

    /**
     * Approvals the caller can act on. Open to any signed-in user: the roles come from the token,
     * never from the request, and a user with nothing to approve gets an empty list.
     */
    @GetMapping("/approvals")
    @RequiresAuthenticated
    public ResponseEntity<List<PendingApprovalDto>> myApprovals(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return ResponseEntity.ok(myApprovalsService.pendingFor(roles));
    }
}
