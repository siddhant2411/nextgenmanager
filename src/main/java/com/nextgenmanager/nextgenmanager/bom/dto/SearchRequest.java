package com.nextgenmanager.nextgenmanager.bom.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SearchRequest {
    private String query;

    public void setQuery(String query) { this.query = query; }
}