package com.nextgenmanager.nextgenmanager.mcp.controller;

import com.nextgenmanager.nextgenmanager.mcp.dto.McpJsonRpcRequest;
import com.nextgenmanager.nextgenmanager.mcp.dto.McpJsonRpcResponse;
import com.nextgenmanager.nextgenmanager.mcp.service.McpServerService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mcp")
@PreAuthorize("isAuthenticated()")
@ConditionalOnProperty(name = "mcp.server.enabled", havingValue = "true", matchIfMissing = true)
public class McpController {
    private final McpServerService mcpServerService;

    public McpController(McpServerService mcpServerService) {
        this.mcpServerService = mcpServerService;
    }

    @PostMapping
    public ResponseEntity<McpJsonRpcResponse> handle(@RequestBody McpJsonRpcRequest request) {
        return ResponseEntity.ok(mcpServerService.handle(request));
    }
}
