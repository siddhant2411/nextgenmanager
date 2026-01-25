package com.nextgenmanager.nextgenmanager.production.mapper;

import com.nextgenmanager.nextgenmanager.production.dto.RoutingOperationDto;
import com.nextgenmanager.nextgenmanager.production.model.RoutingOperation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;

@Mapper(
        componentModel = "spring"
)
public interface  RoutingOperationMapper {

    @Mapping(target = "workCenter", source = "workCenter")
    @Mapping(target = "productionJob", source = "productionJob")
    RoutingOperationDto toDTO(RoutingOperation routingOperation);

}
