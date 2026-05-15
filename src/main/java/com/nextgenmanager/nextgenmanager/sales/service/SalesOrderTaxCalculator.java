package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrderItem;
import com.nextgenmanager.nextgenmanager.sales.model.TaxType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure, stateless tax + totals calculator for Sales Orders.
 * Computes every line's discount, taxable value, CGST/SGST/IGST amounts (server-side)
 * and rolls them up into header totals (subTotal, discountAmount, taxableValue,
 * tax amounts, roundOff, netAmount, totalPayableAmount).
 *
 * Tax type on the SalesOrder must be set BEFORE calling recalculate().
 */
@Service
public class SalesOrderTaxCalculator {

    private static final RoundingMode ROUND = RoundingMode.HALF_UP;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWO     = BigDecimal.valueOf(2);

    public void recalculate(SalesOrder so) {
        if (so.getItems() == null || so.getItems().isEmpty()) {
            zeroHeader(so);
            return;
        }

        TaxType taxType = so.getTaxType() != null ? so.getTaxType() : TaxType.CGST_SGST;

        BigDecimal subTotal     = BigDecimal.ZERO;
        BigDecimal totalCgst    = BigDecimal.ZERO;
        BigDecimal totalSgst    = BigDecimal.ZERO;
        BigDecimal totalIgst    = BigDecimal.ZERO;

        BigDecimal discountPct = nz(so.getDiscountPercentage());

        for (SalesOrderItem line : so.getItems()) {
            calculateLine(line, taxType, discountPct);
            subTotal = subTotal.add(line.getTotalAmountOfProduct());
            totalCgst = totalCgst.add(nz(line.getCgstAmount()));
            totalSgst = totalSgst.add(nz(line.getSgstAmount()));
            totalIgst = totalIgst.add(nz(line.getIgstAmount()));
        }

        BigDecimal discountAmount = subTotal.multiply(discountPct).divide(HUNDRED, 2, ROUND);

        BigDecimal freight = nz(so.getFreightAndForwardingCharges());
        boolean includeFreight = so.isIncludeFreightCharges();

        BigDecimal taxableValue = subTotal.subtract(discountAmount);
        if (includeFreight) {
            taxableValue = taxableValue.add(freight);
        }

        // Header-level tax derived from header taxPercentage (kept for backward compat with UI),
        // but line totals are authoritative when present.
        BigDecimal headerRate = nz(so.getTaxPercentage());
        BigDecimal headerTax = taxableValue.multiply(headerRate).divide(HUNDRED, 2, ROUND);

        // Prefer line-sum if any line carried tax; otherwise fall back to header-rate computation.
        BigDecimal totalTax = totalCgst.add(totalSgst).add(totalIgst);
        if (totalTax.signum() == 0) {
            totalTax = headerTax;
            if (taxType == TaxType.CGST_SGST) {
                BigDecimal half = headerTax.divide(TWO, 2, ROUND);
                totalCgst = half;
                totalSgst = headerTax.subtract(half);
                totalIgst = BigDecimal.ZERO;
            } else {
                totalIgst = headerTax;
                totalCgst = BigDecimal.ZERO;
                totalSgst = BigDecimal.ZERO;
            }
        }

        BigDecimal netAmount = taxableValue.add(totalTax);
        if (!includeFreight) {
            netAmount = netAmount.add(freight);
        }

        BigDecimal rounded = netAmount.setScale(0, ROUND);
        BigDecimal roundOff = rounded.subtract(netAmount).setScale(2, ROUND);

        so.setSubTotal(subTotal.setScale(2, ROUND));
        so.setDiscountAmount(discountAmount);
        so.setTaxableValue(taxableValue.setScale(2, ROUND));
        so.setCgstAmount(totalCgst.setScale(2, ROUND));
        so.setSgstAmount(totalSgst.setScale(2, ROUND));
        so.setIgstAmount(totalIgst.setScale(2, ROUND));
        so.setNetAmount(netAmount.setScale(2, ROUND));
        so.setRoundOffAmount(roundOff);
        so.setTotalPayableAmount(rounded.setScale(2, ROUND));
    }

    private void calculateLine(SalesOrderItem line, TaxType taxType, BigDecimal discountPct) {
        BigDecimal qty   = nz(line.getQty());
        BigDecimal price = nz(line.getPricePerUnit());
        // Line-item discount disabled per requirements
        BigDecimal discountedUnit = price;
        BigDecimal lineTotal = discountedUnit.multiply(qty).setScale(2, ROUND);
        
        // Effective taxable base for GST calculation (after header discount)
        BigDecimal lineTaxableBase = lineTotal.multiply(HUNDRED.subtract(discountPct)).divide(HUNDRED, 4, ROUND);

        line.setUnitPriceAfterDiscount(discountedUnit.setScale(2, ROUND));
        line.setTotalAmountOfProduct(lineTotal);

        // GST rate may have been supplied via cgstRate/sgstRate (split) OR igstRate (full).
        BigDecimal fullRate = inferFullGstRate(line);
        BigDecimal cgst = BigDecimal.ZERO;
        BigDecimal sgst = BigDecimal.ZERO;
        BigDecimal igst = BigDecimal.ZERO;

        if (taxType == TaxType.CGST_SGST) {
            BigDecimal half = fullRate.divide(TWO, 4, ROUND);
            line.setCgstRate(half.setScale(2, ROUND));
            line.setSgstRate(half.setScale(2, ROUND));
            line.setIgstRate(BigDecimal.ZERO);
            cgst = lineTaxableBase.multiply(half).divide(HUNDRED, 2, ROUND);
            sgst = cgst;
        } else {
            line.setCgstRate(BigDecimal.ZERO);
            line.setSgstRate(BigDecimal.ZERO);
            line.setIgstRate(fullRate.setScale(2, ROUND));
            igst = lineTaxableBase.multiply(fullRate).divide(HUNDRED, 2, ROUND);
        }

        line.setCgstAmount(cgst);
        line.setSgstAmount(sgst);
        line.setIgstAmount(igst);
    }

    /**
     * Infers the full GST rate from whichever rate fields are populated on the line.
     * Accepts (cgst+sgst), igst, or a single split value — picks the largest sensible interpretation.
     */
    private BigDecimal inferFullGstRate(SalesOrderItem line) {
        BigDecimal cgst = nz(line.getCgstRate());
        BigDecimal sgst = nz(line.getSgstRate());
        BigDecimal igst = nz(line.getIgstRate());
        if (igst.signum() > 0) return igst;
        if (cgst.signum() > 0 || sgst.signum() > 0) return cgst.add(sgst);
        return BigDecimal.ZERO;
    }

    private void zeroHeader(SalesOrder so) {
        so.setSubTotal(BigDecimal.ZERO);
        so.setDiscountAmount(BigDecimal.ZERO);
        so.setTaxableValue(BigDecimal.ZERO);
        so.setCgstAmount(BigDecimal.ZERO);
        so.setSgstAmount(BigDecimal.ZERO);
        so.setIgstAmount(BigDecimal.ZERO);
        so.setNetAmount(BigDecimal.ZERO);
        so.setRoundOffAmount(BigDecimal.ZERO);
        so.setTotalPayableAmount(BigDecimal.ZERO);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
