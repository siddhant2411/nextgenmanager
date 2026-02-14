package com.nextgenmanager.nextgenmanager.production.mapper;

import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderHistoryDTO;
import com.nextgenmanager.nextgenmanager.production.helper.WorkOrderHistory;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WorkOrderHistoryMapper {

     // Map WorkOrderHistory to WorkOrderHistoryDTO
     public WorkOrderHistoryDTO toDTO(WorkOrderHistory history);

     // Map WorkOrderHistoryDTO to WorkOrderHistory
     public WorkOrderHistory toEntity(WorkOrderHistoryDTO dto);
}
