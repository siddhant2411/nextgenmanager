package com.nextgenmanager.nextgenmanager.bom.service;

import com.nextgenmanager.nextgenmanager.bom.dto.RollupRow;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.model.BomPosition;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class BomExportServiceImpl implements BomExportService{

    @Autowired
    private BomService bomService;

    @Override
    public ByteArrayInputStream exportUnifiedBom(int bomId) {

        Bom bom = bomService.getBom(bomId);
        Map<Integer, RollupRow> rollupMap = bomService.getRolledUpQuantity(bom.getId());
        try (Workbook workbook = new XSSFWorkbook()) {

            Sheet sheet = workbook.createSheet("BOM");
            int rowIdx = 0;

            rowIdx = writeBomHeader(sheet, bom, rowIdx);

            Row headerRow = sheet.createRow(rowIdx++);
            createColumnHeaders(headerRow);

            Set<Long> visited = new HashSet<>();
            writeBomRows(sheet, bom, 0, rowIdx, visited);

            writeRolledUpSheet(workbook,rollupMap);
            autoSizeColumns(sheet);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());


        } catch (IOException e) {
            throw new RuntimeException("Failed to export BOM", e);
        }


    }

    private int writeBomHeader(Sheet sheet, Bom bom, int rowIdx) {

        rowIdx = writeKeyValue(sheet, rowIdx, "BOM Name", bom.getBomName());
        rowIdx = writeKeyValue(sheet, rowIdx, "Parent Item Code", bom.getParentInventoryItem().getItemCode());
        rowIdx = writeKeyValue(sheet, rowIdx, "Parent Item Name", bom.getParentInventoryItem().getName());
        rowIdx = writeKeyValue(sheet, rowIdx, "Drawing Number", bom.getParentInventoryItem().getProductSpecification().getDrawingNumber());
        rowIdx = writeKeyValue(sheet, rowIdx, "Material", bom.getParentInventoryItem().getProductSpecification().getBasicMaterial());
        rowIdx = writeKeyValue(sheet, rowIdx, "Weight", bom.getParentInventoryItem().getProductSpecification().getWeight());
        rowIdx = writeKeyValue(sheet, rowIdx, "Dimension", bom.getParentInventoryItem().getProductSpecification().getDimension());
        rowIdx = writeKeyValue(sheet, rowIdx, "Size", bom.getParentInventoryItem().getProductSpecification().getSize());
        rowIdx = writeKeyValue(sheet, rowIdx, "Revision", bom.getRevision());
        rowIdx = writeKeyValue(sheet, rowIdx, "Status", bom.getBomStatus().name());
        rowIdx = writeKeyValue(sheet, rowIdx, "Effective From", bom.getEffectiveFrom());
        rowIdx = writeKeyValue(sheet, rowIdx, "Effective To", bom.getEffectiveTo());

        return rowIdx + 1; // blank row
    }


    private int writeKeyValue(Sheet sheet, int rowIdx, String key, Object value) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(key);
        row.createCell(1).setCellValue(value != null ? value.toString() : "");
        return rowIdx + 1;
    }

    private void createColumnHeaders(Row row) {
        String[] headers = {
                "Level",
                "Position",
                "Parent Item Code",
                "Component Item Code",
                "Component Name",
                "Item Type",
                "Quantity",
                "UOM",
                "Drawing Number",
                "Material",
                "Weight",
                "Dimension",
                "Size",
                "Revision",
                "Effective From",
                "Effective To",
                "Description"
        };

        for (int i = 0; i < headers.length; i++) {
            row.createCell(i).setCellValue(headers[i]);
        }
    }


    private int writeBomRows(
            Sheet sheet,
            Bom bom,
            int level,
            int rowIdx,
            Set<Long> visited
    ) {
        if (visited.contains(bom.getId())) {
            return rowIdx; // prevent circular BOM
        }

        visited.add((long)bom.getId());

        for (BomPosition pos : bom.getPositions()) {

            InventoryItem item = pos.getChildBom().getParentInventoryItem();
            Row row = sheet.createRow(rowIdx++);

            int col = 0;
            row.createCell(col++).setCellValue(level);
            row.createCell(col++).setCellValue(pos.getPosition());
            row.createCell(col++).setCellValue(bom.getParentInventoryItem().getItemCode());
            row.createCell(col++).setCellValue(item.getItemCode());
            row.createCell(col++).setCellValue(item.getName());
            row.createCell(col++).setCellValue(item.getItemType().name());
            row.createCell(col++).setCellValue(pos.getQuantity());
            row.createCell(col++).setCellValue(item.getUom().name());
            row.createCell(col++).setCellValue(item.getProductSpecification().getDrawingNumber());
            row.createCell(col++).setCellValue(item.getProductSpecification().getBasicMaterial());
            row.createCell(col++).setCellValue(item.getProductSpecification().getWeight());
            row.createCell(col++).setCellValue(item.getProductSpecification().getDimension());
            row.createCell(col++).setCellValue(item.getProductSpecification().getSize());
            row.createCell(col++).setCellValue(bom.getRevision());
            row.createCell(col++).setCellValue( bom.getEffectiveFrom() != null ? bom.getEffectiveFrom().toString() : "");
            row.createCell(col++).setCellValue(
                    bom.getEffectiveTo() != null ? bom.getEffectiveTo().toString() : ""
            );
            row.createCell(col++).setCellValue(bom.getDescription());

            // Recurse into child BOM
            if (pos.getChildBom() != null) {
                rowIdx = writeBomRows(
                        sheet,
                        pos.getChildBom(),
                        level + 1,
                        rowIdx,
                        visited
                );
            }
        }

        return rowIdx;
    }

    private void autoSizeColumns(Sheet sheet) {
        for (int i = 0; i < 11; i++) {
            sheet.autoSizeColumn(i);
        }
    }


    private void writeRolledUpSheet(
            Workbook workbook,
            Map<Integer, RollupRow> rollupMap
    ) {
        Sheet sheet = workbook.createSheet("Rolled-Up Quantity");

        int rowIdx = 0;

        // 1️⃣ Header row
        Row header = sheet.createRow(rowIdx++);
        String[] headers = {
                "Item Code",
                "Item Name",
                "Item Type",
                "Material",
                "Total Qty",
                "UOM"
        };

        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }

        // 2️⃣ Data rows
        for (RollupRow rollup : rollupMap.values()) {

            InventoryItem item = rollup.getItem();

            Row row = sheet.createRow(rowIdx++);
            int col = 0;

            row.createCell(col++).setCellValue(item.getItemCode());
            row.createCell(col++).setCellValue(item.getName());
            row.createCell(col++).setCellValue(item.getItemType().name());
            row.createCell(col++).setCellValue(
                    item.getProductSpecification().getBasicMaterial() != null
                            ? item.getProductSpecification().getBasicMaterial()
                            : ""
            );
            row.createCell(col++).setCellValue(rollup.getTotalQty());
            row.createCell(col++).setCellValue(item.getUom().name());
        }

        // 3️⃣ Auto-size
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

}
