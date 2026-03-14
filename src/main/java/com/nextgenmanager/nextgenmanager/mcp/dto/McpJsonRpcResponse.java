package com.nextgenmanager.nextgenmanager.mcp.dto;

public class McpJsonRpcResponse {
    private final String jsonrpc = "2.0";
    private Object id;
    private Object result;
    private McpJsonRpcError error;

    public static McpJsonRpcResponse success(Object id, Object result) {
        McpJsonRpcResponse response = new McpJsonRpcResponse();
        response.setId(id);
        response.setResult(result);
        return response;
    }

    public static McpJsonRpcResponse error(Object id, int code, String message, Object data) {
        McpJsonRpcResponse response = new McpJsonRpcResponse();
        response.setId(id);
        response.setError(new McpJsonRpcError(code, message, data));
        return response;
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    public Object getId() {
        return id;
    }

    public void setId(Object id) {
        this.id = id;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public McpJsonRpcError getError() {
        return error;
    }

    public void setError(McpJsonRpcError error) {
        this.error = error;
    }
}
