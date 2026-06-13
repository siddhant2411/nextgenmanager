package com.nextgenmanager.nextgenmanager.common.approval.service;

import com.nextgenmanager.nextgenmanager.common.approval.dto.ApprovalPolicyDto;
import com.nextgenmanager.nextgenmanager.common.approval.dto.ApprovalRuleCreateDto;

import java.util.List;

public interface ApprovalPolicyService {
    List<ApprovalPolicyDto> listAll();
    ApprovalPolicyDto getByDocumentType(String documentType);
    ApprovalPolicyDto createOrUpdate(String documentType, boolean enabled);
    ApprovalPolicyDto addRule(String documentType, ApprovalRuleCreateDto ruleDto);
    void deleteRule(Long ruleId);
}
