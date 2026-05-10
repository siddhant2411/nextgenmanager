package com.nextgenmanager.nextgenmanager.purchase.service;

import com.nextgenmanager.nextgenmanager.purchase.model.GstTreatment;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrder;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure, stateless tax calculator — no DB calls.
 * Service layer feeds it a PurchaseOrder with items populated and calls recalculate().
 * All money uses HALF_UP rounding to 2 decimal places.
 */
@Service
public class PurchaseOrderTaxCalculator {

    private static final RoundingMode ROUND = RoundingMode.HALF_UP;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWO     = BigDecimal.valueOf(2);

    /** Recomputes all line amounts and rolls up to PO header totals. Modifies in place. */
    public void recalculate(PurchaseOrder po) {
        if (po.getItems() == null || po.getItems().isEmpty()) {
            zeroHeader(po);
            return;
        }

        GstTreatment treatment = po.getGstTreatment() != null
                ? po.getGstTreatment()
                : GstTreatment.INTRA_STATE;

        BigDecimal totalSubtotal      = BigDecimal.ZERO;
        BigDecimal totalDiscount      = BigDecimal.ZERO;
        BigDecimal totalTaxable       = BigDecimal.ZERO;
        BigDecimal totalCgst          = BigDecimal.ZERO;
        BigDecimal totalSgst          = BigDecimal.ZERO;
        BigDecimal totalIgst          = BigDecimal.ZERO;
        BigDecimal totalCess          = BigDecimal.ZERO;

        for (PurchaseOrderItem line : po.getItems()) {
            calculateLine(line, treatment);
            totalSubtotal = totalSubtotal.add(
                    line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantityOrdered())).setScale(2, ROUND));
            totalDiscount = totalDiscount.add(line.getDiscountAmount());
            totalTaxable  = totalTaxable.add(line.getTaxableValue());
            totalCgst     = totalCgst.add(line.getCgstAmount());
            totalSgst     = totalSgst.add(line.getSgstAmount());
            totalIgst     = totalIgst.add(line.getIgstAmount());
            totalCess     = totalCess.add(line.getCessAmount());
        }

        BigDecimal grandBeforeRound = totalTaxable
                .add(totalCgst).add(totalSgst).add(totalIgst).add(totalCess);
        BigDecimal rounded = grandBeforeRound.setScale(0, ROUND);
        BigDecimal roundOff = rounded.subtract(grandBeforeRound).setScale(2, ROUND);

        po.setSubtotal(totalSubtotal);
        po.setTotalDiscount(totalDiscount);
        po.setTaxableValue(totalTaxable);
        po.setCgstAmount(totalCgst);
        po.setSgstAmount(totalSgst);
        po.setIgstAmount(totalIgst);
        po.setCessAmount(totalCess);
        po.setRoundOff(roundOff);
        po.setGrandTotal(rounded);
    }

    private void calculateLine(PurchaseOrderItem line, GstTreatment treatment) {
        BigDecimal qty   = BigDecimal.valueOf(line.getQuantityOrdered());
        BigDecimal price = line.getUnitPrice() != null ? line.getUnitPrice() : BigDecimal.ZERO;
        BigDecimal rate  = line.getGstRatePct() != null ? line.getGstRatePct() : BigDecimal.ZERO;

        BigDecimal lineBase    = price.multiply(qty).setScale(2, ROUND);
        BigDecimal discPct     = line.getDiscountPct() != null ? line.getDiscountPct() : BigDecimal.ZERO;
        BigDecimal discAmount  = lineBase.multiply(discPct).divide(HUNDRED, 2, ROUND);
        BigDecimal taxable     = lineBase.subtract(discAmount);

        line.setDiscountAmount(discAmount);
        line.setTaxableValue(taxable);

        BigDecimal cgst = BigDecimal.ZERO;
        BigDecimal sgst = BigDecimal.ZERO;
        BigDecimal igst = BigDecimal.ZERO;

        switch (treatment) {
            case INTRA_STATE -> {
                BigDecimal half = rate.divide(TWO, 4, ROUND);
                cgst = taxable.multiply(half).divide(HUNDRED, 2, ROUND);
                sgst = cgst;
            }
            case INTER_STATE -> igst = taxable.multiply(rate).divide(HUNDRED, 2, ROUND);
            // COMPOSITION, EXPORT_WITH_LUT, SEZ, UNREGISTERED → zero tax
            default -> { /* all stay zero */ }
        }

        line.setCgstAmount(cgst);
        line.setSgstAmount(sgst);
        line.setIgstAmount(igst);
        line.setCessAmount(BigDecimal.ZERO);
        line.setLineTotal(taxable.add(cgst).add(sgst).add(igst));
    }

    private void zeroHeader(PurchaseOrder po) {
        po.setSubtotal(BigDecimal.ZERO);
        po.setTotalDiscount(BigDecimal.ZERO);
        po.setTaxableValue(BigDecimal.ZERO);
        po.setCgstAmount(BigDecimal.ZERO);
        po.setSgstAmount(BigDecimal.ZERO);
        po.setIgstAmount(BigDecimal.ZERO);
        po.setCessAmount(BigDecimal.ZERO);
        po.setRoundOff(BigDecimal.ZERO);
        po.setGrandTotal(BigDecimal.ZERO);
    }
}
