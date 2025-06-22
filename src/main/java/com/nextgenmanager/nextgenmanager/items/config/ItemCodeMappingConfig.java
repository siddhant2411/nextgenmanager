package com.nextgenmanager.nextgenmanager.items.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "itemcode")
@Data
public class ItemCodeMappingConfig {

    private List<Mapping> productTypeMappings;
    private List<Mapping> modelCodeMappings;
    private String defaultProductType;
    private String defaultModelCode;
    private String defaultGroupCode;

    @Data
    public static class Mapping {
        private String keyword;
        private String code;
    }
}