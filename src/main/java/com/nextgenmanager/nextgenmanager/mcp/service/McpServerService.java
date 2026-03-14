package com.nextgenmanager.nextgenmanager.mcp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextgenmanager.nextgenmanager.bom.dto.BomDTO;
import com.nextgenmanager.nextgenmanager.bom.dto.BomListDTO;
import com.nextgenmanager.nextgenmanager.bom.service.BomService;
import com.nextgenmanager.nextgenmanager.common.dto.FilterCriteria;
import com.nextgenmanager.nextgenmanager.common.dto.FilterRequest;
import com.nextgenmanager.nextgenmanager.items.DTO.InventoryItemDTO;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.service.InventoryItemService;
import com.nextgenmanager.nextgenmanager.mcp.dto.McpJsonRpcRequest;
import com.nextgenmanager.nextgenmanager.mcp.dto.McpJsonRpcResponse;
import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderDTO;
import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderListDTO;
import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderSummaryDTO;
import com.nextgenmanager.nextgenmanager.production.service.WorkOrderService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class McpServerService {
    private static final String JSON_RPC_VERSION = "2.0";
    private static final String TOOL_INVENTORY_SEARCH = "inventory_search";
    private static final String TOOL_INVENTORY_GET = "inventory_get";
    private static final String TOOL_BOM_GET = "bom_get";
    private static final String TOOL_BOM_SEARCH_ACTIVE = "bom_search_active";
    private static final String TOOL_WORK_ORDER_GET = "work_order_get";
    private static final String TOOL_WORK_ORDER_LIST = "work_order_list";
    private static final String TOOL_WORK_ORDER_SUMMARY = "work_order_summary";
    private static final String RESOURCE_URI_PREFIX = "nextgen://";

    private final String serverName;
    private final String serverVersion;
    private final InventoryItemService inventoryItemService;
    private final BomService bomService;
    private final WorkOrderService workOrderService;
    private final ObjectMapper objectMapper;

    public McpServerService(
            @Value("${mcp.server.name:nextgenmanager-mcp}") String serverName,
            @Value("${mcp.server.version:0.1.0}") String serverVersion,
            InventoryItemService inventoryItemService,
            BomService bomService,
            WorkOrderService workOrderService,
            ObjectMapper objectMapper) {
        this.serverName = serverName;
        this.serverVersion = serverVersion;
        this.inventoryItemService = inventoryItemService;
        this.bomService = bomService;
        this.workOrderService = workOrderService;
        this.objectMapper = objectMapper;
    }

    public McpJsonRpcResponse handle(McpJsonRpcRequest request) {
        Object requestId = request.getId() == null || request.getId().isNull()
                ? null
                : objectMapper.convertValue(request.getId(), Object.class);

        if (request.getJsonrpc() != null && !JSON_RPC_VERSION.equals(request.getJsonrpc())) {
            return McpJsonRpcResponse.error(requestId, -32600, "Invalid Request", "jsonrpc must be 2.0");
        }

        if (request.getMethod() == null || request.getMethod().isBlank()) {
            return McpJsonRpcResponse.error(requestId, -32600, "Invalid Request", "method is required");
        }

        try {
            Object result = switch (request.getMethod()) {
                case "initialize" -> initialize(request.getParams());
                case "ping" -> Map.of();
                case "tools/list" -> listTools();
                case "tools/call" -> callTool(request.getParams());
                case "resources/list" -> listResources();
                case "resources/templates/list" -> listResourceTemplates();
                case "resources/read" -> readResource(request.getParams());
                default -> null;
            };

            if (result == null) {
                return McpJsonRpcResponse.error(requestId, -32601, "Method not found", request.getMethod());
            }

            return McpJsonRpcResponse.success(requestId, result);
        } catch (IllegalArgumentException | EntityNotFoundException e) {
            return McpJsonRpcResponse.error(requestId, -32000, e.getMessage(), null);
        } catch (Exception e) {
            return McpJsonRpcResponse.error(requestId, -32603, "Internal error", e.getMessage());
        }
    }

    private Map<String, Object> initialize(JsonNode params) {
        String protocolVersion = getString(params, "protocolVersion", "2024-11-05");

        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("tools", Map.of("listChanged", false));
        capabilities.put("resources", Map.of("listChanged", false, "subscribe", false));

        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", serverName);
        serverInfo.put("title", "NextGenManager MCP");
        serverInfo.put("version", serverVersion);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("protocolVersion", protocolVersion);
        response.put("capabilities", capabilities);
        response.put("serverInfo", serverInfo);
        response.put("instructions", "Authenticate with the existing JWT bearer token, then use read-only tools and resource URIs to inspect ERP data.");
        return response;
    }

    private Map<String, Object> listTools() {
        return Map.of("tools", List.of(
                tool(TOOL_INVENTORY_SEARCH, "Search inventory items by text or pagination.",
                        schema(
                                integerProperty("page", "Page number, starting at 0."),
                                integerProperty("size", "Page size."),
                                stringProperty("query", "Free-text search query."),
                                stringProperty("sortBy", "Sort field."),
                                stringProperty("sortDir", "Sort direction: asc or desc.")
                        )),
                tool(TOOL_INVENTORY_GET, "Fetch a single inventory item by id.",
                        schemaRequired(List.of("id"), integerProperty("id", "Inventory item id."))),
                tool(TOOL_BOM_GET, "Fetch a BOM by id.",
                        schemaRequired(List.of("id"), integerProperty("id", "BOM id."))),
                tool(TOOL_BOM_SEARCH_ACTIVE, "Search active BOMs by query text.",
                        schema(
                                stringProperty("query", "Free-text search query."),
                                integerProperty("page", "Page number, starting at 0."),
                                integerProperty("size", "Page size.")
                        )),
                tool(TOOL_WORK_ORDER_GET, "Fetch a work order by id.",
                        schemaRequired(List.of("id"), integerProperty("id", "Work order id."))),
                tool(TOOL_WORK_ORDER_LIST, "List work orders with optional status filtering and pagination.",
                        schema(
                                integerProperty("page", "Page number, starting at 0."),
                                integerProperty("size", "Page size."),
                                stringProperty("sortBy", "Sort field."),
                                stringProperty("sortDir", "Sort direction: asc or desc."),
                                stringProperty("status", "Optional status filter such as CREATED or RELEASED.")
                        )),
                tool(TOOL_WORK_ORDER_SUMMARY, "Get work order summary metrics.",
                        schema())
        ));
    }

    private Map<String, Object> listResources() {
        return Map.of("resources", List.of(
                resource("nextgen://guide/mcp", "MCP Usage Guide", "text/plain",
                        "How to use the NextGenManager MCP server and available URI patterns."),
                resource("nextgen://capabilities/read-only", "Read-only Capabilities", "text/plain",
                        "Read-only MCP capabilities exposed by the ERP backend.")
        ));
    }

    private Map<String, Object> listResourceTemplates() {
        return Map.of("resourceTemplates", List.of(
                resourceTemplate(RESOURCE_URI_PREFIX + "inventory/items/{id}", "Inventory Item",
                        "Read a single inventory item by id."),
                resourceTemplate(RESOURCE_URI_PREFIX + "boms/{id}", "BOM",
                        "Read a single BOM by id."),
                resourceTemplate(RESOURCE_URI_PREFIX + "work-orders/{id}", "Work Order",
                        "Read a single work order by id.")
        ));
    }

    private Map<String, Object> callTool(JsonNode params) {
        String toolName = requireString(params, "name");
        JsonNode argumentsNode = params != null ? params.path("arguments") : null;

        Object payload = switch (toolName) {
            case TOOL_INVENTORY_SEARCH -> inventorySearch(argumentsNode);
            case TOOL_INVENTORY_GET -> inventoryGet(argumentsNode);
            case TOOL_BOM_GET -> bomGet(argumentsNode);
            case TOOL_BOM_SEARCH_ACTIVE -> bomSearchActive(argumentsNode);
            case TOOL_WORK_ORDER_GET -> workOrderGet(argumentsNode);
            case TOOL_WORK_ORDER_LIST -> workOrderList(argumentsNode);
            case TOOL_WORK_ORDER_SUMMARY -> workOrderSummary();
            default -> null;
        };

        if (payload == null) {
            throw new IllegalArgumentException("Unknown tool: " + toolName);
        }

        return toolResult(payload);
    }

    private Map<String, Object> readResource(JsonNode params) {
        String uri = requireString(params, "uri");
        Object payload;
        String mimeType = "application/json";

        if ("nextgen://guide/mcp".equals(uri)) {
            payload = """
                    NextGenManager MCP exposes authenticated, read-only tools for inventory, BOM, and work-order data.
                    Use tools/list to discover tools, tools/call to execute them, and resources/templates/list for URI patterns.
                    Example URIs: nextgen://inventory/items/42, nextgen://boms/8, nextgen://work-orders/15
                    """.trim();
            mimeType = "text/plain";
        } else if ("nextgen://capabilities/read-only".equals(uri)) {
            payload = """
                    Supported read-only tools:
                    - inventory_search
                    - inventory_get
                    - bom_get
                    - bom_search_active
                    - work_order_get
                    - work_order_list
                    - work_order_summary
                    """.trim();
            mimeType = "text/plain";
        } else if (uri.startsWith(RESOURCE_URI_PREFIX + "inventory/items/")) {
            payload = inventoryItemService.getInventoryItem(parseIdFromUri(uri));
        } else if (uri.startsWith(RESOURCE_URI_PREFIX + "boms/")) {
            payload = bomService.getBomDTO(parseIdFromUri(uri));
        } else if (uri.startsWith(RESOURCE_URI_PREFIX + "work-orders/")) {
            payload = workOrderService.getWorkOrder(parseIdFromUri(uri));
        } else {
            throw new IllegalArgumentException("Unsupported resource URI: " + uri);
        }

        return Map.of("contents", List.of(resourceContent(uri, mimeType, payload)));
    }

    private Object inventorySearch(JsonNode argumentsNode) {
        int page = getInt(argumentsNode, "page", 0);
        int size = getInt(argumentsNode, "size", 10);
        String query = getString(argumentsNode, "query", "");
        String sortBy = getString(argumentsNode, "sortBy", "inventoryItemId");
        String sortDir = getString(argumentsNode, "sortDir", "asc");
        Page<InventoryItemDTO> items = inventoryItemService.getAllInventoryItems(page, size, sortBy, sortDir, query);
        return normalize(items);
    }

    private Object inventoryGet(JsonNode argumentsNode) {
        int id = requireInt(argumentsNode, "id");
        InventoryItem item = inventoryItemService.getInventoryItem(id);
        return normalize(item);
    }

    private Object bomGet(JsonNode argumentsNode) {
        int id = requireInt(argumentsNode, "id");
        BomDTO bom = bomService.getBomDTO(id);
        return normalize(bom);
    }

    private Object bomSearchActive(JsonNode argumentsNode) {
        String query = requireString(argumentsNode, "query");
        Page<BomListDTO> boms = bomService.searchActiveBom(query);
        return normalize(boms);
    }

    private Object workOrderGet(JsonNode argumentsNode) {
        int id = requireInt(argumentsNode, "id");
        WorkOrderDTO workOrder = workOrderService.getWorkOrder(id);
        return normalize(workOrder);
    }

    private Object workOrderList(JsonNode argumentsNode) {
        FilterRequest request = new FilterRequest();
        request.setPage(getInt(argumentsNode, "page", 0));
        request.setSize(getInt(argumentsNode, "size", 10));
        request.setSortBy(getString(argumentsNode, "sortBy", "id"));
        request.setSortDir(getString(argumentsNode, "sortDir", "desc"));

        String status = getString(argumentsNode, "status", null);
        if (status != null && !status.isBlank()) {
            List<FilterCriteria> filters = new ArrayList<>();
            filters.add(new FilterCriteria("status", "=", status));
            request.setFilters(filters);
        }

        Page<WorkOrderListDTO> workOrders = workOrderService.getAllWorkOrders(request);
        return normalize(workOrders);
    }

    private Object workOrderSummary() {
        WorkOrderSummaryDTO summary = workOrderService.getWorkOrderSummary();
        return normalize(summary);
    }

    private Map<String, Object> toolResult(Object payload) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", List.of(Map.of(
                "type", "text",
                "text", asJson(payload)
        )));
        response.put("structuredContent", payload);
        return response;
    }

    private Map<String, Object> tool(String name, String description, Map<String, Object> inputSchema) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", name);
        tool.put("description", description);
        tool.put("inputSchema", inputSchema);
        return tool;
    }

    private Map<String, Object> schema(Map<String, Object>... properties) {
        return buildSchema(List.of(), properties);
    }

    private Map<String, Object> schemaRequired(List<String> required, Map<String, Object>... properties) {
        return buildSchema(required, properties);
    }

    private Map<String, Object> buildSchema(List<String> required, Map<String, Object>[] properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> propertyMap = new LinkedHashMap<>();
        for (Map<String, Object> property : properties) {
            propertyMap.put((String) property.get("_name"), property.get("_value"));
        }
        schema.put("properties", propertyMap);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    private Map<String, Object> integerProperty(String name, String description) {
        return property(name, Map.of("type", "integer", "description", description));
    }

    private Map<String, Object> stringProperty(String name, String description) {
        return property(name, Map.of("type", "string", "description", description));
    }

    private Map<String, Object> property(String name, Map<String, Object> value) {
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("_name", name);
        wrapper.put("_value", value);
        return wrapper;
    }

    private Map<String, Object> resource(String uri, String name, String mimeType, String description) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("uri", uri);
        resource.put("name", name);
        resource.put("mimeType", mimeType);
        resource.put("description", description);
        return resource;
    }

    private Map<String, Object> resourceTemplate(String uriTemplate, String name, String description) {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("uriTemplate", uriTemplate);
        template.put("name", name);
        template.put("description", description);
        template.put("mimeType", "application/json");
        return template;
    }

    private Map<String, Object> resourceContent(String uri, String mimeType, Object payload) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("uri", uri);
        content.put("mimeType", mimeType);
        if (mimeType.startsWith("text/")) {
            content.put("text", String.valueOf(payload));
        } else {
            content.put("text", asJson(payload));
        }
        return content;
    }

    private Object normalize(Object payload) {
        return objectMapper.convertValue(payload, Object.class);
    }

    private String asJson(Object payload) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize MCP payload", e);
        }
    }

    private String requireString(JsonNode node, String fieldName) {
        String value = getString(node, fieldName, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    private int requireInt(JsonNode node, String fieldName) {
        JsonNode value = node != null ? node.get(fieldName) : null;
        if (value == null || !value.canConvertToInt()) {
            throw new IllegalArgumentException(fieldName + " must be an integer");
        }
        return value.asInt();
    }

    private String getString(JsonNode node, String fieldName, String defaultValue) {
        JsonNode value = node != null ? node.get(fieldName) : null;
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        return value.asText();
    }

    private int getInt(JsonNode node, String fieldName, int defaultValue) {
        JsonNode value = node != null ? node.get(fieldName) : null;
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        if (!value.canConvertToInt()) {
            throw new IllegalArgumentException(fieldName + " must be an integer");
        }
        return value.asInt();
    }

    private int parseIdFromUri(String uri) {
        int separator = uri.lastIndexOf('/');
        if (separator < 0 || separator == uri.length() - 1) {
            throw new IllegalArgumentException("Resource URI must end with an id");
        }
        return Integer.parseInt(uri.substring(separator + 1));
    }
}
