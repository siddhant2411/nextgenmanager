package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.production.dto.TestReportDTO;
import com.nextgenmanager.nextgenmanager.production.dto.TestTemplateDTO;
import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderTestResultDTO;
import com.nextgenmanager.nextgenmanager.production.enums.TestResult;
import com.nextgenmanager.nextgenmanager.production.model.TestTemplate;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderTestResult;
import com.nextgenmanager.nextgenmanager.production.repository.TestTemplateRepository;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderRepository;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderTestResultRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TestTemplateServiceImpl implements TestTemplateService {

    private static final Logger logger = LoggerFactory.getLogger(TestTemplateServiceImpl.class);

    @Autowired
    private TestTemplateRepository testTemplateRepository;

    @Autowired
    private WorkOrderTestResultRepository workOrderTestResultRepository;

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    // ──────────────────────────────── Template CRUD ────────────────────────────────

    @Override
    @Transactional
    public TestTemplateDTO createTestTemplate(TestTemplateDTO dto) {
        InventoryItem item = inventoryItemRepository.findById(dto.getInventoryItemId())
                .orElseThrow(() -> new EntityNotFoundException("InventoryItem not found: " + dto.getInventoryItemId()));

        TestTemplate template = new TestTemplate();
        template.setInventoryItem(item);
        mapDtoToEntity(dto, template);

        template = testTemplateRepository.save(template);
        logger.info("Created TestTemplate id={} for item={}", template.getId(), item.getItemCode());
        return mapEntityToDto(template);
    }

    @Override
    @Transactional
    public TestTemplateDTO updateTestTemplate(Long id, TestTemplateDTO dto) {
        TestTemplate template = testTemplateRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("TestTemplate not found: " + id));

        mapDtoToEntity(dto, template);
        template = testTemplateRepository.save(template);
        logger.info("Updated TestTemplate id={}", template.getId());
        return mapEntityToDto(template);
    }

    @Override
    @Transactional
    public void softDeleteTestTemplate(Long id) {
        TestTemplate template = testTemplateRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("TestTemplate not found: " + id));
        template.setDeletedDate(new Date());
        template.setActive(false);
        testTemplateRepository.save(template);
        logger.info("Soft-deleted TestTemplate id={}", id);
    }

    @Override
    public List<TestTemplateDTO> getTemplatesForItem(int inventoryItemId) {
        return testTemplateRepository
                .findByInventoryItemInventoryItemIdAndActiveTrueAndDeletedDateIsNullOrderBySequence(inventoryItemId)
                .stream()
                .map(this::mapEntityToDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<TestTemplateDTO> getAllTemplatesForItem(int inventoryItemId) {
        return testTemplateRepository
                .findByInventoryItemInventoryItemIdAndDeletedDateIsNullOrderBySequence(inventoryItemId)
                .stream()
                .map(this::mapEntityToDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<TestTemplate> getActiveTemplatesForItem(int inventoryItemId) {
        return testTemplateRepository
                .findByInventoryItemInventoryItemIdAndActiveTrueAndDeletedDateIsNullOrderBySequence(inventoryItemId);
    }

    // ──────────────────────────────── Test Results ────────────────────────────────

    @Override
    public List<WorkOrderTestResultDTO> getTestResultsForWorkOrder(int workOrderId) {
        return workOrderTestResultRepository.findByWorkOrderIdOrderBySequence(workOrderId)
                .stream()
                .map(this::mapResultToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public WorkOrderTestResultDTO recordTestResult(Long testResultId, WorkOrderTestResultDTO dto) {
        WorkOrderTestResult result = workOrderTestResultRepository.findById(testResultId)
                .orElseThrow(() -> new EntityNotFoundException("WorkOrderTestResult not found: " + testResultId));

        // Update actual results
        result.setResultValue(dto.getResultValue());
        result.setTestedQuantity(dto.getTestedQuantity());
        result.setPassedQuantity(dto.getPassedQuantity());
        result.setFailedQuantity(dto.getFailedQuantity());
        result.setResult(dto.getResult() != null ? dto.getResult() : TestResult.PENDING);
        result.setTestedBy(dto.getTestedBy());
        result.setTestDate(dto.getTestDate() != null ? dto.getTestDate() : new Date());
        result.setRemarks(dto.getRemarks());

        // Auto-determine pass/fail based on min/max if result value is provided
        if (dto.getResultValue() != null && result.getMinValue() != null && result.getMaxValue() != null) {
            boolean inRange = dto.getResultValue().compareTo(result.getMinValue()) >= 0
                    && dto.getResultValue().compareTo(result.getMaxValue()) <= 0;
            if (dto.getResult() == null || dto.getResult() == TestResult.PENDING) {
                result.setResult(inRange ? TestResult.PASS : TestResult.FAIL);
            }
        }

        result = workOrderTestResultRepository.save(result);
        logger.info("Recorded test result id={}, result={}", result.getId(), result.getResult());
        return mapResultToDto(result);
    }

    @Override
    public TestReportDTO generateTestReport(int workOrderId) {
        WorkOrder wo = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new EntityNotFoundException("WorkOrder not found: " + workOrderId));

        List<WorkOrderTestResult> results = workOrderTestResultRepository.findByWorkOrderIdOrderBySequence(workOrderId);
        List<WorkOrderTestResultDTO> resultDtos = results.stream()
                .map(this::mapResultToDto)
                .collect(Collectors.toList());

        int total = results.size();
        int completed = (int) results.stream().filter(r -> r.getResult() != TestResult.PENDING).count();
        int passed = (int) results.stream().filter(r -> r.getResult() == TestResult.PASS
                || r.getResult() == TestResult.CONDITIONAL_PASS).count();
        int failed = (int) results.stream().filter(r -> r.getResult() == TestResult.FAIL).count();
        int pending = total - completed;

        // Check if all mandatory tests passed
        boolean overallPass = results.stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsMandatory()))
                .allMatch(r -> r.getResult() == TestResult.PASS || r.getResult() == TestResult.CONDITIONAL_PASS);

        TestReportDTO report = new TestReportDTO();
        report.setWorkOrderId(workOrderId);
        report.setWorkOrderNumber(wo.getWorkOrderNumber());

        if (wo.getBom() != null && wo.getBom().getParentInventoryItem() != null) {
            report.setProductName(wo.getBom().getParentInventoryItem().getName());
            report.setProductCode(wo.getBom().getParentInventoryItem().getItemCode());
        }

        report.setTotalTests(total);
        report.setCompletedTests(completed);
        report.setPassedTests(passed);
        report.setFailedTests(failed);
        report.setPendingTests(pending);
        report.setOverallPass(overallPass);
        report.setReportDate(new Date());
        report.setTestResults(resultDtos);

        return report;
    }

    // ──────────────────────────────── Mappers ────────────────────────────────

    private void mapDtoToEntity(TestTemplateDTO dto, TestTemplate entity) {
        entity.setTestName(dto.getTestName());
        entity.setInspectionType(dto.getInspectionType());
        entity.setSampleSize(dto.getSampleSize());
        entity.setIsMandatory(dto.getIsMandatory() != null ? dto.getIsMandatory() : true);
        entity.setSequence(dto.getSequence());
        entity.setAcceptanceCriteria(dto.getAcceptanceCriteria());
        entity.setUnitOfMeasure(dto.getUnitOfMeasure());
        entity.setMinValue(dto.getMinValue());
        entity.setMaxValue(dto.getMaxValue());
        entity.setActive(dto.isActive());
    }

    private TestTemplateDTO mapEntityToDto(TestTemplate entity) {
        TestTemplateDTO dto = new TestTemplateDTO();
        dto.setId(entity.getId());
        dto.setInventoryItemId(entity.getInventoryItem().getInventoryItemId());
        dto.setTestName(entity.getTestName());
        dto.setInspectionType(entity.getInspectionType());
        dto.setSampleSize(entity.getSampleSize());
        dto.setIsMandatory(entity.getIsMandatory());
        dto.setSequence(entity.getSequence());
        dto.setAcceptanceCriteria(entity.getAcceptanceCriteria());
        dto.setUnitOfMeasure(entity.getUnitOfMeasure());
        dto.setMinValue(entity.getMinValue());
        dto.setMaxValue(entity.getMaxValue());
        dto.setActive(entity.isActive());
        return dto;
    }

    private WorkOrderTestResultDTO mapResultToDto(WorkOrderTestResult entity) {
        WorkOrderTestResultDTO dto = new WorkOrderTestResultDTO();
        dto.setId(entity.getId());
        dto.setWorkOrderId(entity.getWorkOrder().getId());
        dto.setTestTemplateId(entity.getTestTemplate() != null ? entity.getTestTemplate().getId() : null);
        dto.setTestName(entity.getTestName());
        dto.setInspectionType(entity.getInspectionType());
        dto.setSampleSize(entity.getSampleSize());
        dto.setIsMandatory(entity.getIsMandatory());
        dto.setSequence(entity.getSequence());
        dto.setAcceptanceCriteria(entity.getAcceptanceCriteria());
        dto.setUnitOfMeasure(entity.getUnitOfMeasure());
        dto.setMinValue(entity.getMinValue());
        dto.setMaxValue(entity.getMaxValue());
        dto.setResultValue(entity.getResultValue());
        dto.setTestedQuantity(entity.getTestedQuantity());
        dto.setPassedQuantity(entity.getPassedQuantity());
        dto.setFailedQuantity(entity.getFailedQuantity());
        dto.setResult(entity.getResult());
        dto.setTestedBy(entity.getTestedBy());
        dto.setTestDate(entity.getTestDate());
        dto.setRemarks(entity.getRemarks());
        return dto;
    }
}
