package com.nextgenmanager.nextgenmanager.bom.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.model.BomPosition;
import com.nextgenmanager.nextgenmanager.bom.repository.BomRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ProductFinanceSettings;
import com.nextgenmanager.nextgenmanager.items.model.ProductSpecification;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.production.model.RoutingOperation;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

@Service
public class BomExportServiceImpl implements BomExportService {

    @Autowired
    private BomRepository bomRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    private final TemplateEngine templateEngine;

    public BomExportServiceImpl() {
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix("templates/");
        templateResolver.setSuffix(".html");
        templateResolver.setTemplateMode(TemplateMode.HTML);
        templateResolver.setCharacterEncoding("UTF-8");

        this.templateEngine = new TemplateEngine();
        this.templateEngine.setTemplateResolver(templateResolver);
    }

    @Override
    public byte[] generateFlatBomExcel(List<Integer> bomIds) throws Exception {
        List<Bom> boms = bomRepository.findAllById(bomIds);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Flat BOM Export");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle boldStyle = workbook.createCellStyle();
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            boldStyle.setFont(boldFont);

            int currentRow = 0;

            for (Bom bom : boms) {
                InventoryItem parent = bom.getParentInventoryItem();
                ProductSpecification spec = parent.getProductSpecification();

                // Parent Header Info
                Row titleRow = sheet.createRow(currentRow++);
                Cell titleCell = titleRow.createCell(0);
                titleCell.setCellValue("BOM for: " + parent.getItemCode() + " - " + parent.getName());
                titleCell.setCellStyle(boldStyle);

                Row specRow = sheet.createRow(currentRow++);
                specRow.createCell(0).setCellValue("Drawing No: " + (spec != null ? spec.getDrawingNumber() : "N/A"));
                specRow.createCell(2).setCellValue("Rev: " + bom.getRevision());
                specRow.createCell(4).setCellValue("Size/Dim: " + (spec != null ? spec.getSize() + " / " + spec.getDimension() : "N/A"));
                specRow.createCell(6).setCellValue("Weight: " + (spec != null ? spec.getWeight() : "N/A"));

                currentRow++; // Gap

                // Table Headers
                Row tableHeader = sheet.createRow(currentRow++);
                String[] columns = {"Pos", "Component Code", "Component Name", "Qty", "UOM", "Scrap %", "Consumed At Operation", "Work Center", "Unit Cost", "Total Cost"};
                for (int i = 0; i < columns.length; i++) {
                    Cell cell = tableHeader.createCell(i);
                    cell.setCellValue(columns[i]);
                    cell.setCellStyle(headerStyle);
                }

                double totalBomCost = 0;

                for (BomPosition pos : bom.getPositions()) {
                    Row row = sheet.createRow(currentRow++);
                    InventoryItem child = pos.getChildInventoryItem();
                    ProductFinanceSettings finance = child.getProductFinanceSettings();
                    
                    row.createCell(0).setCellValue(pos.getPosition());
                    row.createCell(1).setCellValue(child.getItemCode());
                    row.createCell(2).setCellValue(child.getName());
                    row.createCell(3).setCellValue(pos.getQuantity());
                    row.createCell(4).setCellValue(child.getUom().name());
                    row.createCell(5).setCellValue(pos.getScrapPercentage() != null ? pos.getScrapPercentage().doubleValue() : 0.0);
                    row.createCell(6).setCellValue(pos.getRoutingOperation() != null ? pos.getRoutingOperation().getName() : "N/A");
                    row.createCell(7).setCellValue(pos.getRoutingOperation() != null && pos.getRoutingOperation().getWorkCenter() != null ? pos.getRoutingOperation().getWorkCenter().getCenterName() : "N/A");
                    
                    double unitCost = (finance != null && finance.getStandardCost() != null) ? finance.getStandardCost() : 0.0;
                    double extendedCost = unitCost * pos.getQuantity();
                    totalBomCost += extendedCost;

                    row.createCell(8).setCellValue(unitCost);
                    row.createCell(9).setCellValue(extendedCost);
                }

                Row totalRow = sheet.createRow(currentRow++);
                Cell totalLabel = totalRow.createCell(8);
                totalLabel.setCellValue("Total BOM Cost:");
                totalLabel.setCellStyle(boldStyle);
                totalRow.createCell(9).setCellValue(totalBomCost);

                currentRow += 3; // Space between BOMs
            }

            for (int i = 0; i < 10; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    @Override
    public byte[] generateIndentedBomExcel(List<Integer> bomIds) throws Exception {
        List<Bom> boms = bomRepository.findAllById(bomIds);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Indented BOM Export");
            CellStyle headerStyle = createHeaderStyle(workbook);
            
            int currentRow = 0;
            for (Bom bom : boms) {
                // Header for each Parent BOM
                InventoryItem parent = bom.getParentInventoryItem();
                ProductSpecification spec = parent.getProductSpecification();
                
                Row parentRow = sheet.createRow(currentRow++);
                parentRow.createCell(0).setCellValue("Parent: " + parent.getItemCode() + " | Drawing: " + (spec != null ? spec.getDrawingNumber() : "N/A"));
                
                Row header = sheet.createRow(currentRow++);
                String[] columns = {"Level", "Pos", "Item Code", "Item Name", "Qty", "UOM", "Unit Cost", "Extended Cost"};
                for (int i = 0; i < columns.length; i++) {
                    Cell cell = header.createCell(i);
                    cell.setCellValue(columns[i]);
                    cell.setCellStyle(headerStyle);
                }

                addIndentedRows(sheet, bom, 0, 1.0, new HashSet<>());
                currentRow = sheet.getLastRowNum() + 3;
            }

            for (int i = 0; i < 8; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void addIndentedRows(Sheet sheet, Bom bom, int level, double multiplier, Set<Integer> visitedItems) {
        if (bom == null) return;
        
        int itemId = bom.getParentInventoryItem().getInventoryItemId();
        if (visitedItems.contains(itemId)) return; // Prevent cycles
        visitedItems.add(itemId);

        for (BomPosition pos : bom.getPositions()) {
            int rowIdx = sheet.getLastRowNum() + 1;
            Row row = sheet.createRow(rowIdx);
            
            InventoryItem child = pos.getChildInventoryItem();
            ProductFinanceSettings finance = child.getProductFinanceSettings();
            
            String levelStr = ".".repeat(level) + level;
            row.createCell(0).setCellValue(levelStr);
            row.createCell(1).setCellValue(pos.getPosition());
            row.createCell(2).setCellValue(child.getItemCode());
            row.createCell(3).setCellValue(child.getName());
            
            double totalQty = pos.getQuantity() * multiplier;
            row.createCell(4).setCellValue(totalQty);
            row.createCell(5).setCellValue(child.getUom().name());
            
            double unitCost = (finance != null && finance.getStandardCost() != null) ? finance.getStandardCost() : 0.0;
            row.createCell(6).setCellValue(unitCost);
            row.createCell(7).setCellValue(unitCost * totalQty);

            // Recursively find child BOM if exists
            Optional<Bom> childBom = bomRepository.findActiveBomWithPositionsByParentItemId(child.getInventoryItemId());
            if (childBom.isPresent()) {
                addIndentedRows(sheet, childBom.get(), level + 1, totalQty, new HashSet<>(visitedItems));
            }
        }
    }

    @Override
    public byte[] generateManufacturingBomPdf(List<Integer> bomIds) throws Exception {
        List<Bom> boms = bomRepository.findAllById(bomIds);
        
        Context context = new Context();
        List<Map<String, Object>> bomDatas = new ArrayList<>();

        for (Bom bom : boms) {
            Map<String, Object> data = new HashMap<>();
            data.put("bom", bom);
            data.put("parent", bom.getParentInventoryItem());
            data.put("spec", bom.getParentInventoryItem().getProductSpecification());
            data.put("qrCode", generateQrCodeBase64("BOM:" + bom.getId()));
            data.put("effectiveFrom", bom.getEffectiveFrom());
            data.put("effectiveTo", bom.getEffectiveTo());
            data.put("approvedBy", bom.getApprovedBy());
            data.put("approvalDate", bom.getApprovalDate());

            List<Map<String, Object>> posList = new ArrayList<>();
            for (BomPosition pos : bom.getPositions()) {
                Map<String, Object> p = new HashMap<>();
                p.put("pos", pos.getPosition());
                p.put("code", pos.getChildInventoryItem().getItemCode());
                p.put("name", pos.getChildInventoryItem().getName());
                p.put("qty", pos.getQuantity());
                p.put("uom", pos.getChildInventoryItem().getUom().name());
                p.put("type", pos.getChildInventoryItem().getItemType().name());
                p.put("scrap", pos.getScrapPercentage() != null ? pos.getScrapPercentage() : BigDecimal.ZERO);
                p.put("op", pos.getRoutingOperation() != null ? pos.getRoutingOperation().getName() : "-");
                p.put("wc", (pos.getRoutingOperation() != null && pos.getRoutingOperation().getWorkCenter() != null) ? pos.getRoutingOperation().getWorkCenter().getCenterName() : "-");
                posList.add(p);
            }
            data.put("positions", posList);
            data.put("totalComponents", posList.size());

            if (bom.getRouting() != null && bom.getRouting().getOperations() != null) {
                List<Map<String, Object>> opList = new ArrayList<>();
                BigDecimal totalEstTime = BigDecimal.ZERO;
                for (RoutingOperation op : bom.getRouting().getOperations()) {
                    Map<String, Object> o = new HashMap<>();
                    o.put("seq", op.getSequenceNumber());
                    o.put("name", op.getName());
                    o.put("wc", op.getWorkCenter() != null ? op.getWorkCenter().getCenterName() : "N/A");
                    BigDecimal setup = op.getSetupTime() != null ? op.getSetupTime() : BigDecimal.ZERO;
                    BigDecimal run = op.getRunTime() != null ? op.getRunTime() : BigDecimal.ZERO;
                    BigDecimal total = setup.add(run);
                    totalEstTime = totalEstTime.add(total);
                    o.put("estTime", total.stripTrailingZeros().toPlainString() + " min");
                    opList.add(o);
                }
                data.put("operations", opList);
                data.put("totalEstTime", totalEstTime.stripTrailingZeros().toPlainString() + " min");
            }

            bomDatas.add(data);
        }
        
        context.setVariable("boms", bomDatas);
        String html = templateEngine.process("manufacturing_bom_sheet", context);
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

    @Override
    public byte[] generateBomJobSheet(List<Integer> bomIds) throws Exception {
        List<Bom> boms = bomRepository.findAllById(bomIds);

        Context context = new Context();
        List<Map<String, Object>> bomDatas = new ArrayList<>();

        for (Bom bom : boms) {
            Map<String, Object> data = new HashMap<>();
            data.put("bom", bom);
            data.put("parent", bom.getParentInventoryItem());
            data.put("spec", bom.getParentInventoryItem().getProductSpecification());
            data.put("qrCode", generateQrCodeBase64("WO-BOM:" + bom.getId()));
            data.put("effectiveFrom", bom.getEffectiveFrom());
            data.put("effectiveTo", bom.getEffectiveTo());
            data.put("approvedBy", bom.getApprovedBy());
            data.put("approvalDate", bom.getApprovalDate());

            List<Map<String, Object>> posList = new ArrayList<>();
            for (BomPosition pos : bom.getPositions()) {
                Map<String, Object> p = new HashMap<>();
                p.put("pos", pos.getPosition());
                p.put("code", pos.getChildInventoryItem().getItemCode());
                p.put("name", pos.getChildInventoryItem().getName());
                p.put("qty", pos.getQuantity());
                p.put("uom", pos.getChildInventoryItem().getUom().name());
                p.put("type", pos.getChildInventoryItem().getItemType().name());
                p.put("scrap", pos.getScrapPercentage() != null ? pos.getScrapPercentage() : BigDecimal.ZERO);
                p.put("op", pos.getRoutingOperation() != null ? pos.getRoutingOperation().getName() : "-");
                p.put("wc", pos.getRoutingOperation() != null && pos.getRoutingOperation().getWorkCenter() != null
                        ? pos.getRoutingOperation().getWorkCenter().getCenterName() : "-");
                posList.add(p);
            }
            data.put("positions", posList);
            data.put("totalComponents", posList.size());

            if (bom.getRouting() != null && bom.getRouting().getOperations() != null) {
                List<Map<String, Object>> opList = new ArrayList<>();
                BigDecimal totalEstTime = BigDecimal.ZERO;
                for (RoutingOperation op : bom.getRouting().getOperations()) {
                    Map<String, Object> o = new HashMap<>();
                    o.put("seq", op.getSequenceNumber());
                    o.put("name", op.getName());
                    o.put("wc", op.getWorkCenter() != null ? op.getWorkCenter().getCenterName() : "N/A");
                    BigDecimal setup = op.getSetupTime() != null ? op.getSetupTime() : BigDecimal.ZERO;
                    BigDecimal run = op.getRunTime() != null ? op.getRunTime() : BigDecimal.ZERO;
                    BigDecimal total = setup.add(run);
                    totalEstTime = totalEstTime.add(total);
                    o.put("estTime", total.stripTrailingZeros().toPlainString() + " min");
                    opList.add(o);
                }
                data.put("operations", opList);
                data.put("totalEstTime", totalEstTime.stripTrailingZeros().toPlainString() + " min");
            }

            bomDatas.add(data);
        }

        context.setVariable("boms", bomDatas);
        String html = templateEngine.process("bom_job_sheet", context);
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
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, 200, 200);
        
        try (ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream()) {
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
            byte[] pngData = pngOutputStream.toByteArray();
            return Base64.getEncoder().encodeToString(pngData);
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }
}
