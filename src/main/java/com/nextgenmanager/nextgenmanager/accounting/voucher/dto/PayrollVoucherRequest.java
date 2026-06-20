package com.nextgenmanager.nextgenmanager.accounting.voucher.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Monthly payroll summary. Posts (amounts default to 0 when omitted):
 * <pre>
 *   Dr Salaries & Wages   = grossSalary + employerPf + employerEsi
 *      Cr PF Payable          = employeePf + employerPf
 *      Cr ESI Payable         = employeeEsi + employerEsi
 *      Cr Professional Tax    = professionalTax
 *      Cr TDS Payable         = tds
 *      Cr Salary Payable(net) = grossSalary - employeePf - employeeEsi - professionalTax - tds
 * </pre>
 */
@Data
public class PayrollVoucherRequest {

    @NotNull
    private LocalDate date;

    private String narration;

    /** Gross employee earnings for the period. */
    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal grossSalary;

    private BigDecimal employeePf;
    private BigDecimal employerPf;
    private BigDecimal employeeEsi;
    private BigDecimal employerEsi;
    private BigDecimal professionalTax;
    private BigDecimal tds;
}
