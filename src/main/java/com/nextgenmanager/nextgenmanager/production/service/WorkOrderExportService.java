package com.nextgenmanager.nextgenmanager.production.service;

public interface WorkOrderExportService {
    byte[] generateWorkOrderJobSheet(Integer workOrderId) throws Exception;
    byte[] generateOperationInstructionCards(Integer workOrderId) throws Exception;
    byte[] generateMaterialPickList(Integer workOrderId) throws Exception;
    byte[] generateMoveTickets(Integer workOrderId) throws Exception;
}
