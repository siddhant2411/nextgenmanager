package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.production.dto.TestReportDTO;
import com.nextgenmanager.nextgenmanager.production.dto.TestTemplateDTO;
import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderTestResultDTO;
import com.nextgenmanager.nextgenmanager.production.model.TestTemplate;

import java.util.List;

public interface TestTemplateService {

    // ---- TestTemplate CRUD ----
    TestTemplateDTO createTestTemplate(TestTemplateDTO dto);

    TestTemplateDTO updateTestTemplate(Long id, TestTemplateDTO dto);

    void softDeleteTestTemplate(Long id);

    List<TestTemplateDTO> getTemplatesForItem(int inventoryItemId);

    List<TestTemplateDTO> getAllTemplatesForItem(int inventoryItemId);

    // ---- WorkOrder Test Results ----
    List<WorkOrderTestResultDTO> getTestResultsForWorkOrder(int workOrderId);

    WorkOrderTestResultDTO recordTestResult(Long testResultId, WorkOrderTestResultDTO dto);

    TestReportDTO generateTestReport(int workOrderId);

    // ---- Used internally by WorkOrderService ----
    List<TestTemplate> getActiveTemplatesForItem(int inventoryItemId);
}
