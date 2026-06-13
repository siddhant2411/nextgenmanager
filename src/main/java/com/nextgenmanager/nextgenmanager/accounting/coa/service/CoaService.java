package com.nextgenmanager.nextgenmanager.accounting.coa.service;

import com.nextgenmanager.nextgenmanager.accounting.coa.dto.*;
import com.nextgenmanager.nextgenmanager.accounting.coa.model.LedgerAccount;
import com.nextgenmanager.nextgenmanager.accounting.coa.model.SubLedgerType;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;

import java.util.List;

public interface CoaService {
    AccountGroupDto createGroup(AccountGroupCreateDto dto);
    AccountGroupDto updateGroup(Long id, AccountGroupCreateDto dto);
    void deleteGroup(Long id);
    List<AccountGroupDto> listGroups();
    List<CoaTreeNodeDto> getTree();

    LedgerAccountDto createLedger(LedgerAccountCreateDto dto);
    LedgerAccountDto updateLedger(Long id, LedgerAccountCreateDto dto);
    void deleteLedger(Long id);
    LedgerAccountDto getLedgerById(Long id);
    List<LedgerAccountDto> listLedgers();
    List<LedgerAccountDto> searchLedgers(String query);

    /** Creates or returns a party sub-ledger for a Contact under Sundry Debtors / Sundry Creditors. */
    LedgerAccount getOrCreatePartyLedger(Contact contact, SubLedgerType type);

    /** Returns all ledger accounts already linked to a contact. */
    List<LedgerAccountDto> getContactLedgers(int contactId);

    /** Ensures ledger accounts exist for a contact based on its ContactType; creates missing ones. */
    List<LedgerAccountDto> ensureContactLedgers(int contactId);
}
