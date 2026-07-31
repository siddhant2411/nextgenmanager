package com.nextgenmanager.nextgenmanager.items.service;

import com.nextgenmanager.nextgenmanager.bom.dto.BomCostBreakdownDTO;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.repository.BomRepository;
import com.nextgenmanager.nextgenmanager.bom.service.BomService;
import com.nextgenmanager.nextgenmanager.common.dto.FilterRequest;
import com.nextgenmanager.nextgenmanager.items.DTO.InventoryItemDTO;
import com.nextgenmanager.nextgenmanager.items.DTO.ItemPriceDTO;
import com.nextgenmanager.nextgenmanager.items.DTO.PriceListExportRequest;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds sales price lists as PDF or Excel.
 *
 * <p>A price list only ever contains <em>priced</em> items: rows whose selling price is unset are
 * skipped, since a price list with blank prices is not usable by a sales person.
 *
 * <p>The customer variant discloses list price and GST only. The internal variant additionally
 * discloses the full cost picture — standard cost, fully loaded selling cost, margin — alongside the
 * floor price and maximum discount. Cost is disclosed internally because the floor price is
 * optional on the item master: without it, margin against cost is the only thing that tells a
 * negotiator how far a price can move. Access to the internal variant is gated on the caller's
 * finance visibility in the controller.
 */
@Service
public class PriceListExportService {

    private static final Logger logger = LoggerFactory.getLogger(PriceListExportService.class);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    /** Upper bound when resolving a filter, so an unbounded grid filter cannot exhaust memory. */
    private static final int MAX_FILTER_ROWS = 5000;

    /**
     * Above this many rows the fully loaded BOM cost is not resolved and rows fall back to the
     * stored standard cost. Costing a BOM walks its positions and routing, so doing it for a
     * whole-catalogue export would turn a document download into a long-running job.
     */
    private static final int MAX_BOM_COSTED_ROWS = 300;

    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryItemService inventoryItemService;
    private final BomRepository bomRepository;
    private final BomService bomService;
    private final TemplateEngine templateEngine;

    public PriceListExportService(InventoryItemRepository inventoryItemRepository,
                                  InventoryItemService inventoryItemService,
                                  BomRepository bomRepository,
                                  BomService bomService,
                                  TemplateEngine templateEngine) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.inventoryItemService = inventoryItemService;
        this.bomRepository = bomRepository;
        this.bomService = bomService;
        this.templateEngine = templateEngine;
    }

    /** Resolves the requested items and reduces them to priced, sorted price rows. */
    public List<ItemPriceDTO> buildRows(PriceListExportRequest request) {
        List<ItemPriceDTO> rows = resolveItems(request).stream()
                .map(ItemPriceDTO::from)
                .filter(row -> row.getListPrice() != null && row.getListPrice() > 0d)
                .sorted(Comparator.comparing(ItemPriceDTO::getItemCode,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        // Only the internal sheet shows cost, so only it pays for the BOM roll-up.
        if (request.isInternal()) {
            applyLoadedCosts(rows);
        }
        return rows;
    }

    private List<InventoryItem> resolveItems(PriceListExportRequest request) {
        List<Integer> ids = request.getItemIds();

        if ((ids == null || ids.isEmpty()) && request.getFilter() != null) {
            FilterRequest filter = request.getFilter();
            filter.setPage(0);
            filter.setSize(MAX_FILTER_ROWS);
            ids = inventoryItemService.filterInventoryItems(filter).getContent().stream()
                    .map(InventoryItemDTO::getInventoryItemId)
                    .toList();
            if (ids.isEmpty()) {
                return List.of();
            }
        }

        if (ids != null && !ids.isEmpty()) {
            return inventoryItemRepository.findByInventoryItemIdInAndDeletedDateIsNull(ids);
        }
        return inventoryItemRepository.findAllByDeletedDateIsNull();
    }

    /**
     * Upgrades the stand-in standard cost to the fully loaded selling cost (BOM total incl.
     * overhead) for every row that has an active BOM. Rows without one keep standard cost.
     */
    private void applyLoadedCosts(List<ItemPriceDTO> rows) {
        if (rows.isEmpty() || rows.size() > MAX_BOM_COSTED_ROWS) {
            if (!rows.isEmpty()) {
                logger.info("Price list of {} rows exceeds the {}-row BOM costing limit; " +
                        "selling cost falls back to standard cost", rows.size(), MAX_BOM_COSTED_ROWS);
            }
            return;
        }

        List<Integer> itemIds = rows.stream().map(ItemPriceDTO::getInventoryItemId).toList();
        Map<Integer, Integer> bomIdByItemId = new HashMap<>();
        for (Bom bom : bomRepository.findActiveBomsByParentItemIds(itemIds)) {
            if (bom.getParentInventoryItem() != null) {
                bomIdByItemId.putIfAbsent(bom.getParentInventoryItem().getInventoryItemId(), bom.getId());
            }
        }
        if (bomIdByItemId.isEmpty()) {
            return;
        }

        for (ItemPriceDTO row : rows) {
            Integer bomId = bomIdByItemId.get(row.getInventoryItemId());
            if (bomId == null) {
                continue;
            }
            try {
                BomCostBreakdownDTO breakdown = bomService.getBomCostBreakdown(bomId);
                if (breakdown != null && breakdown.getTotalCost() != null) {
                    row.applyBomSellingCost(breakdown.getTotalCost().doubleValue());
                }
            } catch (Exception e) {
                // A single uncostable BOM must not fail the whole document.
                logger.warn("Price list: could not cost BOM {} for item {} — falling back to standard cost: {}",
                        bomId, row.getItemCode(), e.getMessage());
            }
        }
    }

    // ---------------------------------------------------------------- PDF

    public byte[] generatePdf(PriceListExportRequest request) throws IOException {
        List<ItemPriceDTO> rows = buildRows(request);

        Context context = new Context();
        context.setVariable("rows", rows);
        context.setVariable("internal", request.isInternal());
        context.setVariable("title", request.resolvedTitle());
        context.setVariable("customerName", request.getCustomerName());
        context.setVariable("preparedBy", request.getPreparedBy());
        context.setVariable("validUntil", request.resolvedValidUntil().format(DATE));
        context.setVariable("generatedOn", LocalDate.now().format(DATE));

        String html = templateEngine.process("price_list", context);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        }
    }

    // -------------------------------------------------------------- Excel

    public byte[] generateExcel(PriceListExportRequest request) throws IOException {
        List<ItemPriceDTO> rows = buildRows(request);
        boolean internal = request.isInternal();

        String[] columns = internal
                ? new String[]{"Item Code", "Product", "Specification", "UOM", "HSN Code",
                               "Standard Cost", "Selling Cost", "Last Purchase Cost",
                               "List Price", "Margin %", "Floor Price", "Max Discount %",
                               "GST %", "Price incl. GST"}
                : new String[]{"Item Code", "Product", "Specification", "UOM", "HSN Code",
                               "List Price", "GST %", "Price incl. GST"};

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Price List");

            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);

            CellStyle metaStyle = workbook.createCellStyle();
            Font metaFont = workbook.createFont();
            metaFont.setItalic(true);
            metaStyle.setFont(metaFont);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            CellStyle moneyStyle = workbook.createCellStyle();
            moneyStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

            CellStyle percentStyle = workbook.createCellStyle();
            percentStyle.setDataFormat(workbook.createDataFormat().getFormat("0.00"));

            int rowIdx = 0;

            Row titleRow = sheet.createRow(rowIdx++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(request.resolvedTitle());
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, columns.length - 1));

            Row metaRow = sheet.createRow(rowIdx++);
            Cell metaCell = metaRow.createCell(0);
            StringBuilder meta = new StringBuilder();
            meta.append(internal ? "INTERNAL — NOT FOR CIRCULATION" : "Customer copy");
            meta.append("   |   Valid until: ").append(request.resolvedValidUntil().format(DATE));
            meta.append("   |   Generated: ").append(LocalDate.now().format(DATE));
            if (request.getCustomerName() != null && !request.getCustomerName().isBlank()) {
                meta.append("   |   Prepared for: ").append(request.getCustomerName());
            }
            metaCell.setCellValue(meta.toString());
            metaCell.setCellStyle(metaStyle);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, columns.length - 1));

            rowIdx++; // spacer

            Row headerRow = sheet.createRow(rowIdx++);
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            for (ItemPriceDTO row : rows) {
                Row r = sheet.createRow(rowIdx++);
                int col = 0;
                r.createCell(col++).setCellValue(nullSafe(row.getItemCode()));
                r.createCell(col++).setCellValue(nullSafe(row.getName()));
                r.createCell(col++).setCellValue(nullSafe(row.getSpecification()));
                r.createCell(col++).setCellValue(nullSafe(row.getUom()));
                r.createCell(col++).setCellValue(nullSafe(row.getHsnCode()));

                if (internal) {
                    writeMoney(r.createCell(col++), row.getStandardCost(), moneyStyle, "Not set");
                    writeMoney(r.createCell(col++), row.getSellingCost(), moneyStyle, "Not set");
                    writeMoney(r.createCell(col++), row.getLastPurchaseCost(), moneyStyle, "—");
                }

                Cell listCell = r.createCell(col++);
                listCell.setCellValue(row.getListPrice());
                listCell.setCellStyle(moneyStyle);

                if (internal) {
                    writeMoney(r.createCell(col++), row.getMarginPercent(), percentStyle, "—");
                    writeMoney(r.createCell(col++), row.getFloorPrice(), moneyStyle, "Not set");

                    Cell discCell = r.createCell(col++);
                    if (row.getMaxDiscountPercent() != null) {
                        discCell.setCellValue(row.getMaxDiscountPercent());
                        discCell.setCellStyle(percentStyle);
                    } else {
                        discCell.setCellValue(row.isFloorInvalid() ? "Check floor" : "—");
                    }
                }

                Cell gstCell = r.createCell(col++);
                if (row.getGstRate() != null) {
                    gstCell.setCellValue(row.getGstRate());
                }

                Cell inclCell = r.createCell(col);
                if (row.getPriceInclGst() != null) {
                    inclCell.setCellValue(row.getPriceInclGst());
                    inclCell.setCellStyle(moneyStyle);
                }
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    /** Writes a numeric cell, or {@code placeholder} text when the figure is unavailable. */
    private void writeMoney(Cell cell, Double value, CellStyle style, String placeholder) {
        if (value != null) {
            cell.setCellValue(value);
            cell.setCellStyle(style);
        } else {
            cell.setCellValue(placeholder);
        }
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
