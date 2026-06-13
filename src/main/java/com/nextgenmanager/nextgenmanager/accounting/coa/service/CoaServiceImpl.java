package com.nextgenmanager.nextgenmanager.accounting.coa.service;

import com.nextgenmanager.nextgenmanager.accounting.coa.dto.*;
import com.nextgenmanager.nextgenmanager.accounting.coa.model.*;
import com.nextgenmanager.nextgenmanager.accounting.coa.repository.AccountGroupRepository;
import com.nextgenmanager.nextgenmanager.accounting.coa.repository.LedgerAccountRepository;
import com.nextgenmanager.nextgenmanager.accounting.voucher.repository.VoucherLineRepository;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.contact.repository.ContactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@Transactional
@RequiredArgsConstructor
public class CoaServiceImpl implements CoaService {

    private final AccountGroupRepository groupRepo;
    private final LedgerAccountRepository ledgerRepo;
    private final VoucherLineRepository voucherLineRepo;
    private final ContactRepository contactRepo;

    // ─── Account Groups ──────────────────────────────────────────────────────

    @Override
    public AccountGroupDto createGroup(AccountGroupCreateDto dto) {
        if (groupRepo.existsByCodeAndDeletedDateIsNull(dto.getCode())) {
            throw new IllegalArgumentException("Group code already exists: " + dto.getCode());
        }
        AccountGroup g = new AccountGroup();
        g.setCode(dto.getCode().trim().toUpperCase());
        g.setName(dto.getName().trim());
        g.setNature(dto.getNature());
        g.setScheduleIIIHead(dto.getScheduleIIIHead());
        g.setSortOrder(dto.getSortOrder());
        if (dto.getParentGroupId() != null) {
            AccountGroup parent = groupRepo.findById(dto.getParentGroupId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent group not found: " + dto.getParentGroupId()));
            g.setParentGroup(parent);
        }
        return toGroupDto(groupRepo.save(g));
    }

    @Override
    public AccountGroupDto updateGroup(Long id, AccountGroupCreateDto dto) {
        AccountGroup g = groupRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account group not found: " + id));
        g.setName(dto.getName().trim());
        g.setNature(dto.getNature());
        g.setScheduleIIIHead(dto.getScheduleIIIHead());
        g.setSortOrder(dto.getSortOrder());
        if (dto.getParentGroupId() != null) {
            AccountGroup parent = groupRepo.findById(dto.getParentGroupId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent group not found: " + dto.getParentGroupId()));
            g.setParentGroup(parent);
        } else {
            g.setParentGroup(null);
        }
        return toGroupDto(groupRepo.save(g));
    }

    @Override
    public void deleteGroup(Long id) {
        AccountGroup g = groupRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account group not found: " + id));
        if (!ledgerRepo.findByGroupIdAndDeletedDateIsNull(id).isEmpty()) {
            throw new IllegalStateException("Cannot delete group: it has active ledger accounts.");
        }
        g.setDeletedDate(new Date());
        groupRepo.save(g);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountGroupDto> listGroups() {
        return groupRepo.findByDeletedDateIsNullOrderBySortOrder()
                .stream().map(this::toGroupDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CoaTreeNodeDto> getTree() {
        List<AccountGroup> groups = groupRepo.findAllWithParent();
        List<LedgerAccount> ledgers = ledgerRepo.findByDeletedDateIsNullOrderByCode();

        Map<Long, CoaTreeNodeDto> nodeMap = new LinkedHashMap<>();
        List<CoaTreeNodeDto> roots = new ArrayList<>();

        // Build group nodes
        for (AccountGroup g : groups) {
            CoaTreeNodeDto node = new CoaTreeNodeDto();
            node.setId(g.getId());
            node.setCode(g.getCode());
            node.setName(g.getName());
            node.setNature(g.getNature());
            node.setScheduleIIIHead(g.getScheduleIIIHead());
            node.setSortOrder(g.getSortOrder());
            node.setNodeType("GROUP");
            nodeMap.put(g.getId(), node);
        }

        // Wire parent–child
        for (AccountGroup g : groups) {
            CoaTreeNodeDto node = nodeMap.get(g.getId());
            if (g.getParentGroup() != null) {
                CoaTreeNodeDto parent = nodeMap.get(g.getParentGroup().getId());
                if (parent != null) parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }

        // Attach ledger accounts as leaves under their group node
        for (LedgerAccount la : ledgers) {
            CoaTreeNodeDto node = new CoaTreeNodeDto();
            node.setId(la.getId());
            node.setCode(la.getCode());
            node.setName(la.getName());
            node.setNature(la.getNature());
            node.setNodeType("LEDGER");
            node.setIsControlAccount(la.isControlAccount());
            node.setSubLedgerType(la.getSubLedgerType().name());
            node.setGstApplicable(la.isGstApplicable());

            CoaTreeNodeDto groupNode = nodeMap.get(la.getGroup().getId());
            if (groupNode != null) groupNode.getChildren().add(node);
        }

        return roots;
    }

    // ─── Ledger Accounts ─────────────────────────────────────────────────────

    @Override
    public LedgerAccountDto createLedger(LedgerAccountCreateDto dto) {
        if (ledgerRepo.existsByCodeAndDeletedDateIsNull(dto.getCode())) {
            throw new IllegalArgumentException("Ledger code already exists: " + dto.getCode());
        }
        LedgerAccount la = buildFromDto(dto, new LedgerAccount());
        return toLedgerDto(ledgerRepo.save(la));
    }

    @Override
    public LedgerAccountDto updateLedger(Long id, LedgerAccountCreateDto dto) {
        LedgerAccount la = ledgerRepo.findByIdAndDeletedDateIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ledger account not found: " + id));
        buildFromDto(dto, la);
        return toLedgerDto(ledgerRepo.save(la));
    }

    @Override
    public void deleteLedger(Long id) {
        LedgerAccount la = ledgerRepo.findByIdAndDeletedDateIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ledger account not found: " + id));
        if (la.isSystem()) {
            throw new IllegalStateException("System accounts cannot be deleted: " + la.getCode() + " " + la.getName());
        }
        if (voucherLineRepo.existsByLedgerAccount_IdAndDeletedDateIsNull(id)) {
            throw new IllegalStateException("Cannot delete ledger: it has posted transactions.");
        }
        la.setDeletedDate(new Date());
        ledgerRepo.save(la);
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerAccountDto getLedgerById(Long id) {
        return toLedgerDto(ledgerRepo.findByIdAndDeletedDateIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ledger account not found: " + id)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerAccountDto> listLedgers() {
        return ledgerRepo.findByDeletedDateIsNullOrderByCode().stream().map(this::toLedgerDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerAccountDto> searchLedgers(String query) {
        return ledgerRepo.search(query).stream().map(this::toLedgerDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerAccountDto> getContactLedgers(int contactId) {
        return ledgerRepo.findByContactIdAndDeletedDateIsNull(contactId)
                .stream().map(this::toLedgerDto).toList();
    }

    @Override
    public List<LedgerAccountDto> ensureContactLedgers(int contactId) {
        Contact contact = contactRepo.findById(contactId)
                .orElseThrow(() -> new ResourceNotFoundException("Contact not found: " + contactId));
        List<SubLedgerType> needed = switch (contact.getContactType()) {
            case CUSTOMER -> List.of(SubLedgerType.CUSTOMER);
            case VENDOR   -> List.of(SubLedgerType.VENDOR);
            case BOTH     -> List.of(SubLedgerType.CUSTOMER, SubLedgerType.VENDOR);
        };
        return needed.stream()
                .map(type -> toLedgerDto(getOrCreatePartyLedger(contact, type)))
                .toList();
    }

    @Override
    public LedgerAccount getOrCreatePartyLedger(Contact contact, SubLedgerType type) {
        return ledgerRepo.findByContactIdAndSubLedgerTypeAndDeletedDateIsNull(contact.getId(), type)
                .orElseGet(() -> {
                    String controlCode = type == SubLedgerType.CUSTOMER ? "3010" : "8010";
                    AccountGroup controlGroup = ledgerRepo.findByCodeAndDeletedDateIsNull(controlCode)
                            .map(LedgerAccount::getGroup)
                            .orElseThrow(() -> new IllegalStateException("Control account " + controlCode + " not found"));

                    LedgerAccount la = new LedgerAccount();
                    la.setCode(type.name().charAt(0) + "-" + contact.getId());
                    la.setName(contact.getCompanyName());
                    la.setGroup(controlGroup);
                    la.setNature(controlGroup.getNature());
                    la.setSubLedgerType(type);
                    la.setContact(contact);
                    la.setOpeningBalance(BigDecimal.ZERO);
                    return ledgerRepo.save(la);
                });
    }

    // ─── Mappers (manual — no lazy risk, called inside @Transactional) ────────

    private AccountGroupDto toGroupDto(AccountGroup g) {
        AccountGroupDto dto = new AccountGroupDto();
        dto.setId(g.getId());
        dto.setCode(g.getCode());
        dto.setName(g.getName());
        dto.setNature(g.getNature());
        dto.setScheduleIIIHead(g.getScheduleIIIHead());
        dto.setSortOrder(g.getSortOrder());
        if (g.getParentGroup() != null) {
            dto.setParentGroupId(g.getParentGroup().getId());
            dto.setParentGroupName(g.getParentGroup().getName());
        }
        return dto;
    }

    private LedgerAccountDto toLedgerDto(LedgerAccount la) {
        LedgerAccountDto dto = new LedgerAccountDto();
        dto.setId(la.getId());
        dto.setCode(la.getCode());
        dto.setName(la.getName());
        dto.setGroupId(la.getGroup().getId());
        dto.setGroupName(la.getGroup().getName());
        dto.setNature(la.getNature());
        dto.setOpeningBalance(la.getOpeningBalance());
        dto.setOpeningBalanceDrCr(la.getOpeningBalanceDrCr());
        dto.setControlAccount(la.isControlAccount());
        dto.setSubLedgerType(la.getSubLedgerType());
        dto.setGstApplicable(la.isGstApplicable());
        dto.setGstRate(la.getGstRate());
        dto.setBankAccount(la.isBankAccount());
        dto.setCashAccount(la.isCashAccount());
        dto.setReconcilable(la.isReconcilable());
        dto.setSystem(la.isSystem());
        if (la.getContact() != null) {
            dto.setContactId(la.getContact().getId());
            dto.setContactName(la.getContact().getCompanyName());
        }
        return dto;
    }

    private LedgerAccount buildFromDto(LedgerAccountCreateDto dto, LedgerAccount la) {
        AccountGroup group = groupRepo.findById(dto.getGroupId())
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + dto.getGroupId()));
        la.setCode(dto.getCode().trim());
        la.setName(dto.getName().trim());
        la.setGroup(group);
        // Nature is derived from the parent group — a ledger always inherits its
        // group's nature, which prevents Group=ASSET / Ledger=EXPENSE drift.
        la.setNature(group.getNature());
        la.setOpeningBalance(dto.getOpeningBalance() != null ? dto.getOpeningBalance() : BigDecimal.ZERO);
        la.setOpeningBalanceDrCr(dto.getOpeningBalanceDrCr());
        la.setControlAccount(dto.isControlAccount());
        la.setSubLedgerType(dto.getSubLedgerType() != null ? dto.getSubLedgerType() : SubLedgerType.NONE);
        la.setGstApplicable(dto.isGstApplicable());
        la.setGstRate(dto.getGstRate());
        la.setBankAccount(dto.isBankAccount());
        la.setCashAccount(dto.isCashAccount());
        la.setReconcilable(dto.isReconcilable());
        if (dto.getContactId() != null) {
            Contact c = contactRepo.findById(dto.getContactId())
                    .orElseThrow(() -> new ResourceNotFoundException("Contact not found: " + dto.getContactId()));
            la.setContact(c);
        }
        return la;
    }
}
