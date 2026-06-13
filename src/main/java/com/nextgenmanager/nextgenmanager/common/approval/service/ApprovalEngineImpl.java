package com.nextgenmanager.nextgenmanager.common.approval.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextgenmanager.nextgenmanager.common.approval.dto.ApprovalRequestDto;
import com.nextgenmanager.nextgenmanager.common.approval.dto.ApprovalStepDto;
import com.nextgenmanager.nextgenmanager.common.approval.dto.RequiredStep;
import com.nextgenmanager.nextgenmanager.common.approval.event.ApprovalCompletedEvent;
import com.nextgenmanager.nextgenmanager.common.approval.model.*;
import com.nextgenmanager.nextgenmanager.common.approval.repository.*;
import com.nextgenmanager.nextgenmanager.common.events.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@Transactional
@RequiredArgsConstructor
public class ApprovalEngineImpl implements ApprovalEngine {

    private static final Logger log = LoggerFactory.getLogger(ApprovalEngineImpl.class);

    private final ApprovalPolicyRepository policyRepo;
    private final ApprovalRequestRepository requestRepo;
    private final ApprovalStepRepository stepRepo;
    private final DomainEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<RequiredStep> evaluate(String documentType, Map<String, Object> context) {
        Optional<ApprovalPolicy> policyOpt = policyRepo.findEnabledWithRules(documentType);
        if (policyOpt.isEmpty()) return List.of();

        ApprovalPolicy policy = policyOpt.get();
        List<RequiredStep> steps = new ArrayList<>();

        for (ApprovalRule rule : policy.getRules()) {
            if (rule.getDeletedDate() != null) continue;
            if (conditionsMatch(rule.getConditions(), context)) {
                steps.add(new RequiredStep(rule.getLevel(), rule.getApproverRole()));
            }
        }

        // Deduplicate by level (keep first match per level), sort ascending
        Map<Integer, RequiredStep> byLevel = new LinkedHashMap<>();
        for (RequiredStep s : steps) {
            byLevel.putIfAbsent(s.getLevel(), s);
        }
        List<RequiredStep> result = new ArrayList<>(byLevel.values());
        result.sort(Comparator.comparingInt(RequiredStep::getLevel));
        return result;
    }

    @Override
    public ApprovalRequestDto submit(String documentType, Long documentId, Map<String, Object> context, String requestedBy) {
        List<RequiredStep> requiredSteps = evaluate(documentType, context);

        ApprovalRequest req = new ApprovalRequest();
        req.setDocumentType(documentType);
        req.setDocumentId(documentId);
        req.setStatus(ApprovalStatus.PENDING);
        req.setRequestedBy(requestedBy);

        List<ApprovalStep> steps = new ArrayList<>();
        for (RequiredStep rs : requiredSteps) {
            ApprovalStep step = new ApprovalStep();
            step.setRequest(req);
            step.setLevel(rs.getLevel());
            step.setApproverRole(rs.getApproverRole());
            step.setStatus(StepStatus.PENDING);
            steps.add(step);
        }
        req.setSteps(steps);
        return toDto(requestRepo.save(req));
    }

    @Override
    public void act(Long stepId, boolean approved, String comment, String actedBy) {
        ApprovalStep step = stepRepo.findById(stepId)
                .orElseThrow(() -> new IllegalArgumentException("Approval step not found: " + stepId));

        if (step.getStatus() != StepStatus.PENDING) {
            throw new IllegalStateException("Step " + stepId + " is already " + step.getStatus());
        }

        step.setStatus(approved ? StepStatus.APPROVED : StepStatus.REJECTED);
        step.setActedBy(actedBy);
        step.setActedAt(new Date());
        step.setComment(comment);
        stepRepo.save(step);

        ApprovalRequest req = requestRepo.findByIdWithSteps(step.getRequest().getId())
                .orElseThrow();

        if (!approved) {
            req.setStatus(ApprovalStatus.REJECTED);
            req.setResolvedAt(new Date());
            requestRepo.save(req);
            return;
        }

        // Check if all steps are now approved
        boolean allApproved = req.getSteps().stream()
                .allMatch(s -> s.getStatus() == StepStatus.APPROVED);

        if (allApproved) {
            req.setStatus(ApprovalStatus.APPROVED);
            req.setResolvedAt(new Date());
            requestRepo.save(req);
            eventPublisher.publish(new ApprovalCompletedEvent(req.getDocumentType(), req.getDocumentId(), actedBy));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ApprovalRequestDto getRequest(String documentType, Long documentId) {
        return requestRepo.findByDocumentTypeAndDocumentId(documentType, documentId)
                .map(this::toDto).orElse(null);
    }

    // ─── Condition evaluation ─────────────────────────────────────────────────

    private boolean conditionsMatch(String conditionsJson, Map<String, Object> context) {
        if (conditionsJson == null || conditionsJson.isBlank() || conditionsJson.equals("[]")) return true;
        try {
            List<Map<String, String>> conditions = objectMapper.readValue(
                    conditionsJson, new TypeReference<>() {});
            for (Map<String, String> cond : conditions) {
                if (!evalCondition(cond.get("attribute"), cond.get("operator"), cond.get("value"), context)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("Failed to parse approval rule conditions: {}", conditionsJson, e);
            return false;
        }
    }

    private boolean evalCondition(String attr, String op, String value, Map<String, Object> context) {
        Object ctxVal = context.get(attr);
        if (ctxVal == null) return false;

        return switch (op) {
            case ">="  -> compareNumeric(ctxVal, value) >= 0;
            case "<="  -> compareNumeric(ctxVal, value) <= 0;
            case ">"   -> compareNumeric(ctxVal, value) > 0;
            case "<"   -> compareNumeric(ctxVal, value) < 0;
            case "="   -> ctxVal.toString().equalsIgnoreCase(value);
            case "!="  -> !ctxVal.toString().equalsIgnoreCase(value);
            case "IN"      -> Arrays.asList(value.split(",")).stream()
                               .map(String::trim).anyMatch(v -> v.equalsIgnoreCase(ctxVal.toString()));
            case "NOT_IN"  -> Arrays.asList(value.split(",")).stream()
                               .map(String::trim).noneMatch(v -> v.equalsIgnoreCase(ctxVal.toString()));
            default -> false;
        };
    }

    private int compareNumeric(Object ctxVal, String value) {
        BigDecimal left  = new BigDecimal(ctxVal.toString());
        BigDecimal right = new BigDecimal(value);
        return left.compareTo(right);
    }

    // ─── DTO conversion ───────────────────────────────────────────────────────

    private ApprovalRequestDto toDto(ApprovalRequest req) {
        ApprovalRequestDto dto = new ApprovalRequestDto();
        dto.setId(req.getId());
        dto.setDocumentType(req.getDocumentType());
        dto.setDocumentId(req.getDocumentId());
        dto.setStatus(req.getStatus());
        dto.setRequestedBy(req.getRequestedBy());
        dto.setRequestedAt(req.getRequestedAt());
        dto.setSteps(req.getSteps().stream().map(this::toStepDto).toList());
        return dto;
    }

    private ApprovalStepDto toStepDto(ApprovalStep s) {
        ApprovalStepDto dto = new ApprovalStepDto();
        dto.setId(s.getId());
        dto.setLevel(s.getLevel());
        dto.setApproverRole(s.getApproverRole());
        dto.setStatus(s.getStatus());
        dto.setActedBy(s.getActedBy());
        dto.setActedAt(s.getActedAt());
        dto.setComment(s.getComment());
        return dto;
    }
}
