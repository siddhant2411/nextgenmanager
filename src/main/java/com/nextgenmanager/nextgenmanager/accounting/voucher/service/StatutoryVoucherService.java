package com.nextgenmanager.nextgenmanager.accounting.voucher.service;

import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.DepreciationVoucherRequest;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.PayrollVoucherRequest;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDto;

/** Posts the structured statutory journal vouchers (Phase 4): payroll summary and depreciation. */
public interface StatutoryVoucherService {

    VoucherDto postPayroll(PayrollVoucherRequest req, String username);

    VoucherDto postDepreciation(DepreciationVoucherRequest req, String username);
}
