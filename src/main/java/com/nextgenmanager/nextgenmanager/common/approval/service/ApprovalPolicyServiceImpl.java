package com.nextgenmanager.nextgenmanager.common.approval.service;

import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.common.approval.dto.ApprovalPolicyDto;
import com.nextgenmanager.nextgenmanager.common.approval.dto.ApprovalRuleCreateDto;
import com.nextgenmanager.nextgenmanager.common.approval.model.ApprovalPolicy;
import com.nextgenmanager.nextgenmanager.common.approval.model.ApprovalRule;
import com.nextgenmanager.nextgenmanager.common.approval.repository.ApprovalPolicyRepository;
import com.nextgenmanager.nextgenmanager.common.approval.repository.ApprovalRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class ApprovalPolicyServiceImpl implements ApprovalPolicyService {

    private final ApprovalPolicyRepository policyRepo;
    private final ApprovalRuleRepository ruleRepo;

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalPolicyDto> listAll() {
        return policyRepo.findAll().stream()
                .filter(p -> p.getDeletedDate() == null)
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ApprovalPolicyDto getByDocumentType(String documentType) {
        ApprovalPolicy policy = policyRepo.findByDocumentTypeAndDeletedDateIsNull(documentType)
                .orElseThrow(() -> new ResourceNotFoundException("Approval policy not found for: " + documentType));
        return toDto(policy);
    }

    @Override
    public ApprovalPolicyDto createOrUpdate(String documentType, boolean enabled) {
        ApprovalPolicy policy = policyRepo.findByDocumentTypeAndDeletedDateIsNull(documentType)
                .orElseGet(() -> {
                    ApprovalPolicy p = new ApprovalPolicy();
                    p.setDocumentType(documentType);
                    return p;
                });
        policy.setEnabled(enabled);
        return toDto(policyRepo.save(policy));
    }

    @Override
    public ApprovalPolicyDto addRule(String documentType, ApprovalRuleCreateDto ruleDto) {
        ApprovalPolicy policy = policyRepo.findByDocumentTypeAndDeletedDateIsNull(documentType)
                .orElseThrow(() -> new ResourceNotFoundException("Approval policy not found for: " + documentType));
        ApprovalRule rule = new ApprovalRule();
        rule.setPolicy(policy);
        rule.setSortOrder(ruleDto.getSortOrder());
        rule.setConditions(ruleDto.getConditions());
        rule.setApproverRole(ruleDto.getApproverRole());
        rule.setLevel(ruleDto.getLevel());
        rule.setDescription(ruleDto.getDescription());
        policy.getRules().add(rule);
        return toDto(policyRepo.save(policy));
    }

    @Override
    public void deleteRule(Long ruleId) {
        ApprovalRule rule = ruleRepo.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("Approval rule not found: " + ruleId));
        rule.getPolicy().getRules().remove(rule);
        ruleRepo.delete(rule);
    }

    // ─── Mapping ──────────────────────────────────────────────────────────────

    private ApprovalPolicyDto toDto(ApprovalPolicy policy) {
        ApprovalPolicyDto dto = new ApprovalPolicyDto();
        dto.setId(policy.getId());
        dto.setDocumentType(policy.getDocumentType());
        dto.setDescription(policy.getDescription());
        dto.setEnabled(policy.isEnabled());
        dto.setRules(policy.getRules().stream()
                .filter(r -> r.getDeletedDate() == null)
                .map(r -> {
                    ApprovalPolicyDto.ApprovalRuleDto rd = new ApprovalPolicyDto.ApprovalRuleDto();
                    rd.setId(r.getId());
                    rd.setSortOrder(r.getSortOrder());
                    rd.setConditions(r.getConditions());
                    rd.setApproverRole(r.getApproverRole());
                    rd.setLevel(r.getLevel());
                    rd.setDescription(r.getDescription());
                    return rd;
                }).collect(Collectors.toList()));
        return dto;
    }
}
