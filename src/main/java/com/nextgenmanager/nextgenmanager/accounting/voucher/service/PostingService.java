package com.nextgenmanager.nextgenmanager.accounting.voucher.service;

import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDto;

public interface PostingService {
    /**
     * Validates, numbers, and persists a voucher.
     * Status will be POSTED or PENDING_APPROVAL depending on ApprovalEngine evaluation.
     * Throws UnbalancedVoucherException, InvalidVoucherException, LockedPeriodException.
     */
    VoucherDto post(VoucherDraft draft, String username);

    /**
     * Creates a mirror reversal voucher (Dr↔Cr swapped) linked to the original.
     * Original voucher is marked REVERSED. Both are POSTED immediately (reversals bypass approval).
     */
    VoucherDto reverse(Long voucherId, String reason, String username);
}
