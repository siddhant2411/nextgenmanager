package com.nextgenmanager.nextgenmanager.bom.dto;


import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.production.dto.RoutingDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BOMRoutingRequestMapper {

    private BomRequestDTO bom;

    private RoutingDto routing;

    public Bom toBomEntity() {
        return bom != null ? bom.toEntity() : null;
    }
}
