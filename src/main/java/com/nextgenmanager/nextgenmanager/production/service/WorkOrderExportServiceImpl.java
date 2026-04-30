package com.nextgenmanager.nextgenmanager.production.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.items.model.ProductSpecification;
import com.nextgenmanager.nextgenmanager.production.model.RoutingOperation;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderMaterial;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderOperation;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WorkOrderExportServiceImpl implements WorkOrderExportService {

    @Autowired
    private WorkOrderRepository workOrderRepository;

    private final TemplateEngine templateEngine;

    public WorkOrderExportServiceImpl() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        this.templateEngine = new TemplateEngine();
        this.templateEngine.setTemplateResolver(resolver);
    }

    // ====================================================================
    // 1. EXISTING - Work Order Job Sheet (original)
    // ====================================================================
    @Override
    public byte[] generateWorkOrderJobSheet(Integer workOrderId) throws Exception {
        WorkOrder wo = findWorkOrder(workOrderId);

        Context context = new Context();

        Bom bom = wo.getBom();
        String parentCode = bom != null && bom.getParentInventoryItem() != null
                ? bom.getParentInventoryItem().getItemCode() : "N/A";
        String parentName = bom != null && bom.getParentInventoryItem() != null
                ? bom.getParentInventoryItem().getName() : "N/A";
        String bomCode = bom != null
                ? (bom.getBomName() != null ? bom.getBomName() : "BOM #" + bom.getId()) : "N/A";
        String bomRevision = bom != null ? String.valueOf(bom.getRevision()) : "N/A";
        String firstPassYield = wo.getFirstPassYield().stripTrailingZeros().toPlainString() + "%";

        ProductSpecification spec = (bom != null && bom.getParentInventoryItem() != null)
                ? bom.getParentInventoryItem().getProductSpecification() : null;
        String drawingNo = spec != null && spec.getDrawingNumber() != null ? spec.getDrawingNumber() : "N/A";
        String size = spec != null && spec.getSize() != null ? spec.getSize() : "N/A";
        String weight = spec != null && spec.getWeight() != null ? spec.getWeight() : "N/A";
        String basicMaterial = spec != null && spec.getBasicMaterial() != null ? spec.getBasicMaterial() : "N/A";

        context.setVariable("wo", wo);
        context.setVariable("parentCode", parentCode);
        context.setVariable("parentName", parentName);
        context.setVariable("bomCode", bomCode);
        context.setVariable("bomRevision", bomRevision);
        context.setVariable("drawingNo", drawingNo);
        context.setVariable("size", size);
        context.setVariable("weight", weight);
        context.setVariable("basicMaterial", basicMaterial);
        context.setVariable("firstPassYield", firstPassYield);
        context.setVariable("materials", buildMaterialRows(wo));
        context.setVariable("operations", buildOperationRows(wo));
        context.setVariable("qrCode", generateQrCodeBase64("WO:" + wo.getWorkOrderNumber()));

        String html = templateEngine.process("work_order_job_sheet", context);
        return renderPdf(html);
    }

    // ====================================================================
    // 2. NEW - Operation Instruction Cards
    // ====================================================================
    @Override
    public byte[] generateOperationInstructionCards(Integer workOrderId) throws Exception {
        WorkOrder wo = findWorkOrder(workOrderId);
        Bom bom = wo.getBom();

        String parentCode = bom != null && bom.getParentInventoryItem() != null
                ? bom.getParentInventoryItem().getItemCode() : "N/A";
        String parentName = bom != null && bom.getParentInventoryItem() != null
                ? bom.getParentInventoryItem().getName() : "N/A";

        String dueDate = wo.getDueDate() != null
                ? new java.text.SimpleDateFormat("dd-MMM-yyyy").format(wo.getDueDate()) : "N/A";

        // Build one card per operation
        List<Map<String, Object>> cards = new ArrayList<>();
        if (wo.getOperations() != null) {
            // Get materials grouped by operation ID for quick lookup
            Map<Long, List<WorkOrderMaterial>> materialsByOp = new HashMap<>();
            if (wo.getMaterials() != null) {
                for (WorkOrderMaterial m : wo.getMaterials()) {
                    if (m.getDeletedDate() != null) continue;
                    Long opId = m.getWorkOrderOperation() != null ? m.getWorkOrderOperation().getId() : null;
                    if (opId != null) {
                        materialsByOp.computeIfAbsent(opId, k -> new ArrayList<>()).add(m);
                    }
                }
            }

            wo.getOperations().stream()
                    .filter(op -> op.getDeletedDate() == null)
                    .sorted(Comparator.comparingInt(WorkOrderOperation::getSequence))
                    .forEach(op -> {
                        Map<String, Object> card = new HashMap<>();
                        card.put("seq", op.getSequence());
                        card.put("operationName", op.getOperationName());
                        card.put("workCenter", op.getWorkCenter() != null ? op.getWorkCenter().getCenterName() : "-");
                        card.put("machine", op.getAssignedMachine() != null ? op.getAssignedMachine().getMachineName() : "-");
                        card.put("plannedQty", op.getPlannedQuantity());
                        card.put("status", op.getStatus().name());

                        // Get details from routing operation if available
                        RoutingOperation routingOp = op.getRoutingOperation();
                        card.put("laborRole", routingOp != null && routingOp.getLaborRole() != null
                                ? routingOp.getLaborRole().getRoleName() : "-");
                        card.put("numOperators", routingOp != null && routingOp.getNumberOfOperators() != null
                                ? routingOp.getNumberOfOperators() : 1);
                        card.put("setupTime", routingOp != null && routingOp.getSetupTime() != null
                                ? routingOp.getSetupTime().stripTrailingZeros().toPlainString() + " min" : "-");
                        card.put("runTime", routingOp != null && routingOp.getRunTime() != null
                                ? routingOp.getRunTime().stripTrailingZeros().toPlainString() + " min" : "-");
                        card.put("inspection", routingOp != null && routingOp.getInspection() != null
                                && routingOp.getInspection());
                        card.put("notes", routingOp != null ? routingOp.getNotes() : null);
                        card.put("availableInputQty", op.getAvailableInputQuantity());

                        // Materials for this specific operation
                        List<Map<String, Object>> opMaterials = new ArrayList<>();
                        List<WorkOrderMaterial> mats = materialsByOp.getOrDefault(op.getId(), Collections.emptyList());
                        for (WorkOrderMaterial m : mats) {
                            Map<String, Object> matRow = new HashMap<>();
                            matRow.put("code", m.getComponent().getItemCode());
                            matRow.put("name", m.getComponent().getName());
                            matRow.put("uom", m.getComponent().getUom().name());
                            matRow.put("plannedQty", m.getPlannedRequiredQuantity());
                            matRow.put("issuedQty", m.getIssuedQuantity());
                            opMaterials.add(matRow);
                        }
                        card.put("materials", opMaterials);

                        cards.add(card);
                    });
        }

        Context context = new Context();
        context.setVariable("cards", cards);
        context.setVariable("woNumber", wo.getWorkOrderNumber());
        context.setVariable("parentCode", parentCode);
        context.setVariable("parentName", parentName);
        context.setVariable("orderQty", wo.getPlannedQuantity());
        context.setVariable("dueDate", dueDate);
        context.setVariable("qrCode", generateQrCodeBase64("WO:" + wo.getWorkOrderNumber()));

        String html = templateEngine.process("wo_operation_instruction_card", context);
        return renderPdf(html);
    }

    // ====================================================================
    // 4. NEW - Material Pick List
    // ====================================================================
    @Override
    public byte[] generateMaterialPickList(Integer workOrderId) throws Exception {
        WorkOrder wo = findWorkOrder(workOrderId);
        Bom bom = wo.getBom();

        String parentCode = bom != null && bom.getParentInventoryItem() != null
                ? bom.getParentInventoryItem().getItemCode() : "N/A";
        String parentName = bom != null && bom.getParentInventoryItem() != null
                ? bom.getParentInventoryItem().getName() : "N/A";

        String dueDate = wo.getDueDate() != null
                ? new java.text.SimpleDateFormat("dd-MMM-yyyy").format(wo.getDueDate()) : "N/A";

        List<Map<String, Object>> materials = buildMaterialRows(wo);

        Context context = new Context();
        context.setVariable("woNumber", wo.getWorkOrderNumber());
        context.setVariable("parentCode", parentCode);
        context.setVariable("parentName", parentName);
        context.setVariable("orderQty", wo.getPlannedQuantity());
        context.setVariable("dueDate", dueDate);
        context.setVariable("priority", wo.getPriority() != null ? wo.getPriority().name() : "-");
        context.setVariable("remarks", wo.getRemarks());
        context.setVariable("materials", materials);
        context.setVariable("totalItems", materials.size());
        context.setVariable("qrCode", generateQrCodeBase64("PICK:" + wo.getWorkOrderNumber()));

        String html = templateEngine.process("wo_material_pick_list", context);
        return renderPdf(html);
    }

    // ====================================================================
    // 5. NEW - Move Tickets / Routing Tags
    // ====================================================================
    @Override
    public byte[] generateMoveTickets(Integer workOrderId) throws Exception {
        WorkOrder wo = findWorkOrder(workOrderId);
        Bom bom = wo.getBom();

        String parentCode = bom != null && bom.getParentInventoryItem() != null
                ? bom.getParentInventoryItem().getItemCode() : "N/A";

        String dueDate = wo.getDueDate() != null
                ? new java.text.SimpleDateFormat("dd-MMM-yyyy").format(wo.getDueDate()) : "N/A";

        // Build tickets - one per operation, sorted by sequence
        List<Map<String, Object>> tickets = new ArrayList<>();
        if (wo.getOperations() != null) {
            List<WorkOrderOperation> sortedOps = wo.getOperations().stream()
                    .filter(op -> op.getDeletedDate() == null)
                    .sorted(Comparator.comparingInt(WorkOrderOperation::getSequence))
                    .collect(Collectors.toList());

            for (int i = 0; i < sortedOps.size(); i++) {
                WorkOrderOperation op = sortedOps.get(i);
                Map<String, Object> ticket = new HashMap<>();
                ticket.put("seq", op.getSequence());
                ticket.put("operationName", op.getOperationName());
                ticket.put("workCenter", op.getWorkCenter() != null ? op.getWorkCenter().getCenterName() : "-");
                ticket.put("machine", op.getAssignedMachine() != null ? op.getAssignedMachine().getMachineName() : "-");
                ticket.put("plannedQty", op.getPlannedQuantity());

                // Next station info
                if (i + 1 < sortedOps.size()) {
                    WorkOrderOperation nextOp = sortedOps.get(i + 1);
                    String nextWc = nextOp.getWorkCenter() != null ? nextOp.getWorkCenter().getCenterName() : "?";
                    String nextMachine = nextOp.getAssignedMachine() != null ? nextOp.getAssignedMachine().getMachineName() : "";
                    ticket.put("nextStation", nextWc + (!nextMachine.isEmpty() ? " / " + nextMachine : "")
                            + " (" + nextOp.getOperationName() + ")");
                } else {
                    ticket.put("nextStation", "FINISHED GOODS / QC");
                }

                tickets.add(ticket);
            }
        }

        Context context = new Context();
        context.setVariable("tickets", tickets);
        context.setVariable("woNumber", wo.getWorkOrderNumber());
        context.setVariable("parentCode", parentCode);
        context.setVariable("dueDate", dueDate);
        context.setVariable("qrCode", generateQrCodeBase64("WO:" + wo.getWorkOrderNumber()));

        String html = templateEngine.process("wo_move_ticket", context);
        return renderPdf(html);
    }

    // ====================================================================
    // Helper Methods
    // ====================================================================

    private WorkOrder findWorkOrder(Integer id) {
        return workOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Work order not found: " + id));
    }

    private List<Map<String, Object>> buildMaterialRows(WorkOrder wo) {
        List<Map<String, Object>> materials = new ArrayList<>();
        if (wo.getMaterials() != null) {
            for (WorkOrderMaterial m : wo.getMaterials()) {
                if (m.getDeletedDate() != null) continue;
                Map<String, Object> row = new HashMap<>();
                row.put("code", m.getComponent().getItemCode());
                row.put("name", m.getComponent().getName());
                row.put("uom", m.getComponent().getUom().name());
                row.put("netQty", m.getNetRequiredQuantity());
                row.put("plannedQty", m.getPlannedRequiredQuantity());
                row.put("issuedQty", m.getIssuedQuantity());
                BigDecimal remaining = m.getPlannedRequiredQuantity().subtract(m.getIssuedQuantity());
                row.put("remaining", remaining.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remaining);
                row.put("status", m.getIssueStatus().name());
                row.put("opName", m.getWorkOrderOperation() != null ? m.getWorkOrderOperation().getOperationName() : "-");
                materials.add(row);
            }
        }
        return materials;
    }

    private List<Map<String, Object>> buildOperationRows(WorkOrder wo) {
        List<Map<String, Object>> operations = new ArrayList<>();
        if (wo.getOperations() != null) {
            wo.getOperations().stream()
                    .filter(op -> op.getDeletedDate() == null)
                    .sorted(Comparator.comparingInt(WorkOrderOperation::getSequence))
                    .forEach(op -> {
                        Map<String, Object> row = new HashMap<>();
                        row.put("seq", op.getSequence());
                        row.put("name", op.getOperationName());
                        row.put("wc", op.getWorkCenter() != null ? op.getWorkCenter().getCenterName() : "-");
                        row.put("machine", op.getAssignedMachine() != null ? op.getAssignedMachine().getMachineName() : "-");
                        row.put("status", op.getStatus().name());
                        row.put("plannedQty", op.getPlannedQuantity());
                        row.put("completedQty", op.getCompletedQuantity());
                        row.put("plannedStart", op.getPlannedStartDate());
                        row.put("plannedEnd", op.getPlannedEndDate());
                        row.put("actualStart", op.getActualStartDate());
                        row.put("actualEnd", op.getActualEndDate());
                        operations.add(row);
                    });
        }
        return operations;
    }

    private byte[] renderPdf(String html) throws Exception {
        // Sanitize HTML to replace undefined entities like &nbsp;
        html = html.replace("&nbsp;", "&#160;");
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        }
    }

    private String generateQrCodeBase64(String text) throws Exception {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, 200, 200);
        try (ByteArrayOutputStream png = new ByteArrayOutputStream()) {
            MatrixToImageWriter.writeToStream(matrix, "PNG", png);
            return Base64.getEncoder().encodeToString(png.toByteArray());
        }
    }
}
