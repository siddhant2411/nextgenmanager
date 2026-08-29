package com.nextgenmanager.nextgenmanager.common.controller;

import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresAuthenticated;
import com.nextgenmanager.nextgenmanager.marketing.ailead.AiLeadAgentProperties;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Which optional features this particular deployment actually has.
 *
 * Some capabilities depend on a service the operator may not run at all -- they are switched on
 * by configuration, not by a role. Roles answer "may this user do it"; this answers "does it
 * exist here", and the UI needs both before it decides whether to offer a screen. Without this,
 * the frontend would have to guess from a failed call, which shows the user a tab that only
 * breaks when clicked.
 *
 * Authenticated rather than public: the shape of a deployment is not something to hand out at
 * the login screen, and nothing reads this before sign-in.
 */
@RestController
@RequestMapping("/api/features")
@RequiresAuthenticated
public class FeatureController {

    private final AiLeadAgentProperties aiLeadAgentProperties;

    public FeatureController(AiLeadAgentProperties aiLeadAgentProperties) {
        this.aiLeadAgentProperties = aiLeadAgentProperties;
    }

    @GetMapping
    @Operation(summary = "Optional features enabled by this server's configuration")
    public ResponseEntity<Map<String, Boolean>> features() {
        return ResponseEntity.ok(Map.of(
                "aiLeadAgent", aiLeadAgentProperties.isEnabled()
        ));
    }
}
