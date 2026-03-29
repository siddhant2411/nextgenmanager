package com.nextgenmanager.nextgenmanager.mcp.dto;

public class McpJsonRpcError {
    private int code;
    private String message;
    private Object data;

    public McpJsonRpcError() {
    }

    public McpJsonRpcError(int code, String message, Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}
