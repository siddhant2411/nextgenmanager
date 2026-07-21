package com.nextgenmanager.nextgenmanager.production.service.workorder;

import com.nextgenmanager.nextgenmanager.production.dto.CostOfProductionDTO;
import com.nextgenmanager.nextgenmanager.production.dto.MaterialCostLineItemDTO;
import com.nextgenmanager.nextgenmanager.production.dto.OperationCostLineItemDTO;
import com.nextgenmanager.nextgenmanager.production.enums.CostType;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderLabourEntry;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderMaterial;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderOperation;
import com.nextgenmanager.nextgenmanager.bom.model.routing.RoutingOperation;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenter;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class CostOfProductionServiceImpl implements CostOfProductionService {

    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);
    private static final int SCALE = 4;
    private static final RoundingMode RM = RoundingMode.HALF_UP;

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Override
    @Transactional(readOnly = true)
    public CostOfProductionDTO compute(int workOrderId) {
        WorkOrder wo = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Work order not found: " + workOrderId));

        List<MaterialCostLineItemDTO> matLines = buildMaterialLines(wo);
        List<OperationCostLineItemDTO> opLines = buildOperationLines(wo);

        BigDecimal estMat = sum(matLines, MaterialCostLineItemDTO::getEstimatedCost);
        BigDecimal actMat = sum(matLines, MaterialCostLineItemDTO::getActualCost);

        BigDecimal estLabour  = sum(opLines, OperationCostLineItemDTO::getEstimatedLabourCost);
        BigDecimal estMachine = sum(opLines, OperationCostLineItemDTO::getEstimatedMachineCost);

        BigDecimal actLabour  = sum(opLines, OperationCostLineItemDTO::getActualLabourCost);
        BigDecimal actMachine = sum(opLines, OperationCostLineItemDTO::getActualMachineCost);

        // Flat operation costs (SUB_CONTRACTED / FIXED_RATE) don't decompose into labour/machine/
        // overhead — they only carry a line total. Sum them into their own bucket so they're counted
        // in the grand total (previously they were silently dropped).
        BigDecimal estSub = sumFlat(opLines, OperationCostLineItemDTO::getEstimatedTotalCost);
        BigDecimal actSub = sumFlat(opLines, OperationCostLineItemDTO::getActualTotalCost);

        // Blanket manufacturing overhead: a single per-BOM % at this level's rate on everything except
        // subcontracted operations (their vendor price already carries overhead). Sub-assembly material
        // is included (child standard costs are stored prime). Replaces per-work-center overhead.
        BigDecimal overheadPct = wo.getBom() != null && wo.getBom().getOverheadPercentage() != null
                ? wo.getBom().getOverheadPercentage() : BigDecimal.ZERO;
        BigDecimal estSubOnly = sumByType(opLines, OperationCostLineItemDTO::getEstimatedTotalCost, CostType.SUB_CONTRACTED);
        BigDecimal actSubOnly = sumByType(opLines, OperationCostLineItemDTO::getActualTotalCost, CostType.SUB_CONTRACTED);
        BigDecimal estOvhd = estMat.add(estLabour).add(estMachine).add(estSub).subtract(estSubOnly)
                .multiply(overheadPct).divide(BigDecimal.valueOf(100), SCALE, RM).setScale(2, RM);
        BigDecimal actOvhd = actMat.add(actLabour).add(actMachine).add(actSub).subtract(actSubOnly)
                .multiply(overheadPct).divide(BigDecimal.valueOf(100), SCALE, RM).setScale(2, RM);

        BigDecimal totalEst = estMat.add(estLabour).add(estMachine).add(estOvhd).add(estSub);
        BigDecimal totalAct = actMat.add(actLabour).add(actMachine).add(actOvhd).add(actSub);
        BigDecimal totalVar = totalAct.subtract(totalEst);

        BigDecimal completedQty = safe(wo.getCompletedQuantity());
        BigDecimal plannedQty   = safe(wo.getPlannedQuantity());

        BigDecimal estPerUnit = divide(totalEst, plannedQty);
        BigDecimal actPerUnit = divide(totalAct, completedQty);

        BigDecimal varPct = totalEst.compareTo(BigDecimal.ZERO) != 0
                ? totalVar.divide(totalEst, SCALE, RM).multiply(BigDecimal.valueOf(100)).setScale(2, RM)
                : BigDecimal.ZERO;

        String itemName = "";
        String itemCode = "";
        if (wo.getBom() != null && wo.getBom().getParentInventoryItem() != null) {
            itemName = wo.getBom().getParentInventoryItem().getName();
            itemCode = wo.getBom().getParentInventoryItem().getItemCode();
        }

        return CostOfProductionDTO.builder()
                .workOrderNumber(wo.getWorkOrderNumber())
                .itemName(itemName)
                .itemCode(itemCode)
                .plannedQuantity(plannedQty)
                .completedQuantity(completedQty)
                .estimatedMaterialCost(scale2(estMat))
                .estimatedLabourCost(scale2(estLabour))
                .estimatedMachineCost(scale2(estMachine))
                .estimatedOverheadCost(scale2(estOvhd))
                .estimatedSubcontractCost(scale2(estSub))
                .totalEstimatedCost(scale2(totalEst))
                .estimatedCostPerUnit(scale2(estPerUnit))
                .actualMaterialCost(scale2(actMat))
                .actualLabourCost(scale2(actLabour))
                .actualMachineCost(scale2(actMachine))
                .actualOverheadCost(scale2(actOvhd))
                .actualSubcontractCost(scale2(actSub))
                .totalActualCost(scale2(totalAct))
                .actualCostPerUnit(scale2(actPerUnit))
                .materialVariance(scale2(actMat.subtract(estMat)))
                .labourVariance(scale2(actLabour.subtract(estLabour)))
                .machineVariance(scale2(actMachine.subtract(estMachine)))
                .overheadVariance(scale2(actOvhd.subtract(estOvhd)))
                .subcontractVariance(scale2(actSub.subtract(estSub)))
                .totalVariance(scale2(totalVar))
                .totalVariancePercentage(varPct)
                .materials(matLines)
                .operations(opLines)
                .build();
    }

    // ─── Material lines ───────────────────────────────────────────────────────

    private List<MaterialCostLineItemDTO> buildMaterialLines(WorkOrder wo) {
        List<MaterialCostLineItemDTO> lines = new ArrayList<>();
        for (WorkOrderMaterial mat : wo.getMaterials()) {
            if (mat.getDeletedDate() != null) continue;

            BigDecimal stdCost = getStandardCost(mat);
            BigDecimal planned = safe(mat.getNetRequiredQuantity());
            BigDecimal consumed = safe(mat.getConsumedQuantity());
            BigDecimal estCost = planned.multiply(stdCost).setScale(2, RM);
            BigDecimal actCost = consumed.multiply(stdCost).setScale(2, RM);

            String code = "";
            String name = "";
            if (mat.getComponent() != null) {
                code = mat.getComponent().getItemCode() != null ? mat.getComponent().getItemCode() : "";
                name = mat.getComponent().getName() != null ? mat.getComponent().getName() : "";
            }

            lines.add(MaterialCostLineItemDTO.builder()
                    .itemCode(code)
                    .itemName(name)
                    .standardCost(scale2(stdCost))
                    .plannedQuantity(planned)
                    .consumedQuantity(consumed)
                    .estimatedCost(estCost)
                    .actualCost(actCost)
                    .variance(scale2(actCost.subtract(estCost)))
                    .build());
        }
        return lines;
    }

    private BigDecimal getStandardCost(WorkOrderMaterial mat) {
        try {
            if (mat.getComponent() != null
                    && mat.getComponent().getProductFinanceSettings() != null
                    && mat.getComponent().getProductFinanceSettings().getStandardCost() != null) {
                return BigDecimal.valueOf(mat.getComponent().getProductFinanceSettings().getStandardCost());
            }
        } catch (Exception ignored) {}
        return BigDecimal.ZERO;
    }


    // ─── Operation lines ──────────────────────────────────────────────────────

    private List<OperationCostLineItemDTO> buildOperationLines(WorkOrder wo) {
        List<OperationCostLineItemDTO> lines = new ArrayList<>();
        for (WorkOrderOperation op : wo.getOperations()) {
            if (op.getDeletedDate() != null) continue;

            RoutingOperation ro = op.getRoutingOperation();

            CostType costType = ro != null && ro.getCostType() != null ? ro.getCostType() : CostType.CALCULATED;

            // ── Subcontract operations: flat rate × qty, no time breakdown ──
            if (costType == CostType.SUB_CONTRACTED && ro != null) {
                BigDecimal rate       = ro.getFixedCostPerUnit() != null ? ro.getFixedCostPerUnit() : BigDecimal.ZERO;
                BigDecimal plannedQty = safe(op.getPlannedQuantity());
                BigDecimal doneQty    = safe(op.getCompletedQuantity());
                BigDecimal est        = rate.multiply(plannedQty).setScale(2, RM);
                BigDecimal act        = rate.multiply(doneQty).setScale(2, RM);
                WorkCenter wc = op.getWorkCenter() != null ? op.getWorkCenter() : ro.getWorkCenter();
                lines.add(OperationCostLineItemDTO.builder()
                        .sequence(op.getSequence())
                        .operationName(op.getOperationName())
                        .workCenterName(wc != null ? wc.getCenterName() : "")
                        .costType(CostType.SUB_CONTRACTED)
                        .subcontractRatePerUnit(scale2(rate))
                        .plannedQuantity(plannedQty)
                        .completedQuantity(doneQty)
                        .estimatedTotalCost(est)
                        .actualTotalCost(act)
                        .variance(scale2(act.subtract(est)))
                        .build());
                continue;
            }

            // ── Fixed-rate operations: flat rate × qty, no time breakdown ──
            if (costType == CostType.FIXED_RATE && ro != null && ro.getFixedCostPerUnit() != null) {
                BigDecimal rate       = ro.getFixedCostPerUnit();
                BigDecimal plannedQty = safe(op.getPlannedQuantity());
                BigDecimal doneQty    = safe(op.getCompletedQuantity());
                BigDecimal est        = rate.multiply(plannedQty).setScale(2, RM);
                BigDecimal act        = rate.multiply(doneQty).setScale(2, RM);
                WorkCenter wc = op.getWorkCenter() != null ? op.getWorkCenter() : ro.getWorkCenter();
                lines.add(OperationCostLineItemDTO.builder()
                        .sequence(op.getSequence())
                        .operationName(op.getOperationName())
                        .workCenterName(wc != null ? wc.getCenterName() : "")
                        .costType(CostType.FIXED_RATE)
                        .subcontractRatePerUnit(scale2(rate))
                        .plannedQuantity(plannedQty)
                        .completedQuantity(doneQty)
                        .estimatedTotalCost(est)
                        .actualTotalCost(act)
                        .variance(scale2(act.subtract(est)))
                        .build());
                continue;
            }

            // ── Piece-rate operations: rate × eaches-per-unit × qty, no time breakdown ──
            if (costType == CostType.RATE_TIMES_QTY && ro != null) {
                BigDecimal rate = ro.getCostRate() != null
                        ? ro.getCostRate()
                        : (ro.getProductionJob() != null && ro.getProductionJob().getDefaultPieceRate() != null
                            ? ro.getProductionJob().getDefaultPieceRate()
                            : BigDecimal.ZERO);
                BigDecimal eachesPerUnit = safe(ro.getCostQuantity());
                BigDecimal plannedQty    = safe(op.getPlannedQuantity());
                BigDecimal doneQty       = safe(op.getCompletedQuantity());
                WorkCenter wc = op.getWorkCenter() != null ? op.getWorkCenter() : ro.getWorkCenter();

                // No per-op overhead — the blanket BOM overhead is applied once in compute().
                BigDecimal estBase     = rate.multiply(eachesPerUnit).multiply(plannedQty).setScale(2, RM);
                BigDecimal actBase     = rate.multiply(eachesPerUnit).multiply(doneQty).setScale(2, RM);

                // Base lands in the labour bucket so the header total (labour+machine) counts it.
                lines.add(OperationCostLineItemDTO.builder()
                        .sequence(op.getSequence())
                        .operationName(op.getOperationName())
                        .workCenterName(wc != null ? wc.getCenterName() : "")
                        .costType(CostType.RATE_TIMES_QTY)
                        .subcontractRatePerUnit(scale2(rate))
                        .plannedQuantity(plannedQty)
                        .completedQuantity(doneQty)
                        .estimatedLabourCost(estBase)
                        .estimatedTotalCost(estBase)
                        .actualLabourCost(actBase)
                        .actualTotalCost(actBase)
                        .variance(scale2(actBase.subtract(estBase)))
                        .build());
                continue;
            }

            BigDecimal setupTime   = ro != null && ro.getSetupTime()  != null ? ro.getSetupTime()  : BigDecimal.ZERO;
            BigDecimal runTime     = ro != null && ro.getRunTime()    != null ? ro.getRunTime()    : BigDecimal.ZERO;
            BigDecimal operators   = ro != null && ro.getNumberOfOperators() != null
                    ? BigDecimal.valueOf(ro.getNumberOfOperators()) : BigDecimal.ONE;
            BigDecimal labourRate  = ro != null && ro.getLaborRole() != null && ro.getLaborRole().getCostPerHour() != null
                    ? ro.getLaborRole().getCostPerHour() : BigDecimal.ZERO;

            WorkCenter wc = op.getWorkCenter() != null ? op.getWorkCenter()
                    : (ro != null ? ro.getWorkCenter() : null);
            BigDecimal machineRate  = wc != null && wc.getMachineCostPerHour() != null  ? wc.getMachineCostPerHour()  : BigDecimal.ZERO;

            BigDecimal plannedQty    = safe(op.getPlannedQuantity());
            BigDecimal completedQty  = safe(op.getCompletedQuantity());
            BigDecimal totalPlanned  = setupTime.add(runTime.multiply(plannedQty));

            // Estimated — direct conversion only; blanket overhead is applied once in compute().
            BigDecimal estHours      = divide(totalPlanned, SIXTY);
            BigDecimal estLabour     = estHours.multiply(labourRate).multiply(operators).setScale(2, RM);
            BigDecimal estMachine    = estHours.multiply(machineRate).setScale(2, RM);
            BigDecimal estTotal      = estLabour.add(estMachine);

            // Actual labour from logged entries
            BigDecimal actLabourCost    = BigDecimal.ZERO;
            BigDecimal actLabourMinutes = BigDecimal.ZERO;
            for (WorkOrderLabourEntry entry : op.getLabourEntries()) {
                if (entry.getDeletedDate() != null) continue;
                if (entry.getTotalCost() != null)       actLabourCost    = actLabourCost.add(entry.getTotalCost());
                if (entry.getDurationMinutes() != null) actLabourMinutes = actLabourMinutes.add(entry.getDurationMinutes());
            }

            // Actual machine: use operation actual dates if available, else routing rate on completed qty
            BigDecimal actMachineMinutes = resolveActualMachineMinutes(op, runTime, completedQty);
            BigDecimal actMachine   = divide(actMachineMinutes, SIXTY).multiply(machineRate).setScale(2, RM);
            BigDecimal actTotal     = actLabourCost.add(actMachine);

            String wcName = wc != null ? wc.getCenterName() : "";

            lines.add(OperationCostLineItemDTO.builder()
                    .sequence(op.getSequence())
                    .operationName(op.getOperationName())
                    .workCenterName(wcName)
                    .costType(costType)
                    .plannedQuantity(plannedQty)
                    .completedQuantity(completedQty)
                    .setupTimeMinutes(scale2(setupTime))
                    .runTimePerUnitMinutes(scale2(runTime))
                    .totalPlannedMinutes(scale2(totalPlanned))
                    .actualLabourMinutes(scale2(actLabourMinutes))
                    .laborCostPerHour(scale2(labourRate))
                    .numberOfOperators(operators)
                    .machineCostPerHour(scale2(machineRate))
                    .estimatedLabourCost(scale2(estLabour))
                    .estimatedMachineCost(scale2(estMachine))
                    .estimatedTotalCost(scale2(estTotal))
                    .actualLabourCost(scale2(actLabourCost))
                    .actualMachineCost(scale2(actMachine))
                    .actualTotalCost(scale2(actTotal))
                    .variance(scale2(actTotal.subtract(estTotal)))
                    .build());
        }
        lines.sort((a, b) -> {
            if (a.getSequence() == null) return 1;
            if (b.getSequence() == null) return -1;
            return a.getSequence().compareTo(b.getSequence());
        });
        return lines;
    }

    private BigDecimal resolveActualMachineMinutes(WorkOrderOperation op,
                                                    BigDecimal runTime, BigDecimal completedQty) {
        // 1. Use operation actual start/end dates — real wall-clock machine run time
        if (op.getActualStartDate() != null && op.getActualEndDate() != null) {
            long millis = op.getActualEndDate().getTime() - op.getActualStartDate().getTime();
            if (millis > 0) return BigDecimal.valueOf(millis / 60_000.0).setScale(SCALE, RM);
        }
        // 2. Routing run time scaled to actual completed qty (proportional to production output)
        return runTime.multiply(completedQty);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface Extractor<T> {
        BigDecimal get(T t);
    }

    /** Sum an extractor over only the flat (SUB_CONTRACTED / FIXED_RATE) operation lines. */
    private BigDecimal sumFlat(List<OperationCostLineItemDTO> list, Extractor<OperationCostLineItemDTO> fn) {
        return list.stream()
                .filter(l -> l.getCostType() == CostType.SUB_CONTRACTED || l.getCostType() == CostType.FIXED_RATE)
                .map(fn::get)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Sum an extractor over only the operation lines of a specific cost type. */
    private BigDecimal sumByType(List<OperationCostLineItemDTO> list, Extractor<OperationCostLineItemDTO> fn, CostType type) {
        return list.stream()
                .filter(l -> l.getCostType() == type)
                .map(fn::get)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private <T> BigDecimal sum(List<T> list, Extractor<T> fn) {
        return list.stream()
                .map(fn::get)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal safe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private BigDecimal divide(BigDecimal num, BigDecimal den) {
        if (den == null || den.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return num.divide(den, SCALE, RM);
    }

    private BigDecimal scale2(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(2, RM);
    }
}
