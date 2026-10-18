package com.nextgenmanager.nextgenmanager.accounting.coa.service;

import com.nextgenmanager.nextgenmanager.accounting.coa.dto.*;
import com.nextgenmanager.nextgenmanager.accounting.coa.model.*;
import com.nextgenmanager.nextgenmanager.accounting.coa.repository.AccountGroupRepository;
import com.nextgenmanager.nextgenmanager.accounting.coa.repository.LedgerAccountRepository;
import com.nextgenmanager.nextgenmanager.accounting.voucher.repository.VoucherLineRepository;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.common.audit.AuditEvent;
import com.nextgenmanager.nextgenmanager.common.audit.service.AuditTrailService;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.contact.repository.ContactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

import static com.nextgenmanager.nextgenmanager.common.audit.AuditActions.*;

@Service
@Transactional
@RequiredArgsConstructor
public class CoaServiceImpl implements CoaService {

    private final AccountGroupRepository groupRepo;
    private final LedgerAccountRepository ledgerRepo;
    private final VoucherLineRepository voucherLineRepo;
    private final ContactRepository contactRepo;
    private final AuditTrailService auditTrail;

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
        AccountGroup saved = groupRepo.save(g);
        auditTrail.record(AuditEvent.of(CREATE, ACCOUNT_GROUP, saved.getId()).label(groupLabel(saved))
                .summary("Group created").details(groupSnapshot(saved)));
        return toGroupDto(saved);
    }

    @Override
    public AccountGroupDto updateGroup(Long id, AccountGroupCreateDto dto) {
        AccountGroup g = groupRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account group not found: " + id));
        Map<String, Object> before = groupSnapshot(g);
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
        AccountGroup saved = groupRepo.save(g);
        Map<String, Object> changed = AuditTrailService.changes(before, groupSnapshot(saved));
        if (!changed.isEmpty()) {
            auditTrail.record(AuditEvent.of(UPDATE, ACCOUNT_GROUP, saved.getId()).label(groupLabel(saved))
                    .summary("Changed " + String.join(", ", changed.keySet())).details(changed));
        }
        return toGroupDto(saved);
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
        auditTrail.record(AuditEvent.of(DELETE, ACCOUNT_GROUP, g.getId()).label(groupLabel(g))
                .summary("Group deleted").details(groupSnapshot(g)));
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
        LedgerAccount la = ledgerRepo.save(buildFromDto(dto, new LedgerAccount()));
        auditTrail.record(AuditEvent.of(CREATE, LEDGER, la.getId()).label(ledgerLabel(la))
                .summary("Ledger created").details(ledgerSnapshot(la)));
        return toLedgerDto(la);
    }

    @Override
    public LedgerAccountDto updateLedger(Long id, LedgerAccountCreateDto dto) {
        LedgerAccount la = ledgerRepo.findByIdAndDeletedDateIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ledger account not found: " + id));
        // Recorded field by field: an opening balance typed onto a ledger changes every report without
        // a voucher, so the old and new figures, and who changed them, must be kept.
        Map<String, Object> before = ledgerSnapshot(la);
        buildFromDto(dto, la);
        LedgerAccount saved = ledgerRepo.save(la);
        Map<String, Object> changed = AuditTrailService.changes(before, ledgerSnapshot(saved));
        if (!changed.isEmpty()) {
            auditTrail.record(AuditEvent.of(UPDATE, LEDGER, saved.getId()).label(ledgerLabel(saved))
                    .summary("Changed " + String.join(", ", changed.keySet())).details(changed));
        }
        return toLedgerDto(saved);
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
        auditTrail.record(AuditEvent.of(DELETE, LEDGER, la.getId()).label(ledgerLabel(la))
                .summary("Ledger deleted").details(ledgerSnapshot(la)));
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
        return subLedgerTypesFor(contact).stream()
                .map(type -> toLedgerDto(getOrCreatePartyLedger(contact, type)))
                .toList();
    }

    @Override
    public BulkLedgerResultDto ensureContactLedgersBulk(List<Integer> contactIds) {
        // Sweeping every contact, not just GST-registered ones: a party without a
        // GSTIN still trades and still carries an opening balance, and neither has
        // anywhere to post until its sub-ledger exists.
        List<Contact> targets = (contactIds == null || contactIds.isEmpty())
                ? contactRepo.findAllActive()
                : contactRepo.findAllById(contactIds);

        BulkLedgerResultDto result = new BulkLedgerResultDto();
        for (Contact contact : targets) {
            if (contact.getDeletedDate() != null) continue;
            result.setProcessed(result.getProcessed() + 1);
            for (SubLedgerType type : subLedgerTypesFor(contact)) {
                try {
                    boolean existed = ledgerRepo
                            .findByContactIdAndSubLedgerTypeAndDeletedDateIsNull(contact.getId(), type)
                            .isPresent();
                    getOrCreatePartyLedger(contact, type);
                    if (existed) result.setExisting(result.getExisting() + 1);
                    else result.setCreated(result.getCreated() + 1);
                } catch (RuntimeException e) {
                    // A missing control account (3010 / 8010) fails every contact the same
                    // way. Recording it per contact keeps the caller's report honest instead
                    // of aborting a 300-row import on the first bad one.
                    result.getFailed().add(new BulkLedgerResultDto.Failure(
                            contact.getId(), contact.getCompanyName(),
                            type + ": " + e.getMessage()));
                }
            }
        }
        return result;
    }

    /** CUSTOMER gets a debtor ledger, VENDOR a creditor ledger, BOTH gets both. */
    private List<SubLedgerType> subLedgerTypesFor(Contact contact) {
        return switch (contact.getContactType()) {
            case CUSTOMER -> List.of(SubLedgerType.CUSTOMER);
            case VENDOR   -> List.of(SubLedgerType.VENDOR);
            case BOTH     -> List.of(SubLedgerType.CUSTOMER, SubLedgerType.VENDOR);
        };
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
                    LedgerAccount saved = ledgerRepo.save(la);
                    auditTrail.record(AuditEvent.of(CREATE, LEDGER, saved.getId()).label(ledgerLabel(saved))
                            .summary("Party ledger created for " + contact.getCompanyName()).details(ledgerSnapshot(saved)));
                    return saved;
                });
    }

    // ─── Audit snapshots: the fields a person would check, as plain values ────

    private static String ledgerLabel(LedgerAccount la) {
        return la.getCode() + " " + la.getName();
    }

    private static String groupLabel(AccountGroup g) {
        return g.getCode() + " " + g.getName();
    }

    private static Map<String, Object> ledgerSnapshot(LedgerAccount la) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", la.getCode());
        m.put("name", la.getName());
        m.put("group", la.getGroup() != null ? groupLabel(la.getGroup()) : null);
        m.put("openingBalance", la.getOpeningBalance());
        m.put("openingBalanceDrCr", la.getOpeningBalanceDrCr());
        m.put("subLedgerType", la.getSubLedgerType() != null ? la.getSubLedgerType().name() : null);
        m.put("controlAccount", la.isControlAccount());
        m.put("bankAccount", la.isBankAccount());
        m.put("cashAccount", la.isCashAccount());
        m.put("reconcilable", la.isReconcilable());
        m.put("gstApplicable", la.isGstApplicable());
        m.put("gstRate", la.getGstRate());
        m.put("contact", la.getContact() != null ? la.getContact().getCompanyName() : null);
        return m;
    }

    private static Map<String, Object> groupSnapshot(AccountGroup g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", g.getCode());
        m.put("name", g.getName());
        m.put("parentGroup", g.getParentGroup() != null ? groupLabel(g.getParentGroup()) : null);
        m.put("nature", g.getNature() != null ? g.getNature().name() : null);
        m.put("scheduleIIIHead", g.getScheduleIIIHead());
        m.put("sortOrder", g.getSortOrder());
        return m;
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
