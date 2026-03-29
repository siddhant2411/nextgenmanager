package com.nextgenmanager.nextgenmanager.production.dto;

import com.nextgenmanager.nextgenmanager.production.enums.RoutingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Builder
@AllArgsConstructor
@Getter
@Setter
public class RoutingDto {

    private Long id;
    private Integer bomId;
    private RoutingStatus status;

    private String createdBy;

    private List<RoutingOperationDto> operations;
}
