package com.nextgenmanager.nextgenmanager.items.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.nextgenmanager.nextgenmanager.Inventory.repository.GoodsReceiptItemRepository;
import com.nextgenmanager.nextgenmanager.items.model.ItemType;
import com.nextgenmanager.nextgenmanager.items.model.ItemVendorPrice;
import com.nextgenmanager.nextgenmanager.items.model.PriceType;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.items.repository.ItemVendorPriceRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

@Service
public class InventoryItemExportService {

    private final InventoryItemRepository inventoryItemRepository;
    private final ItemVendorPriceRepository itemVendorPriceRepository;
    private final GoodsReceiptItemRepository goodsReceiptItemRepository;
    private final TemplateEngine templateEngine;
    private final String frontendUrl;

    public InventoryItemExportService(
            InventoryItemRepository inventoryItemRepository,
            ItemVendorPriceRepository itemVendorPriceRepository,
            GoodsReceiptItemRepository goodsReceiptItemRepository,
            TemplateEngine templateEngine,
            @Value("${frontend.url:http://localhost:3000}") String frontendUrl) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.itemVendorPriceRepository = itemVendorPriceRepository;
        this.goodsReceiptItemRepository = goodsReceiptItemRepository;
        this.templateEngine = templateEngine;
        this.frontendUrl = frontendUrl;
    }

    private List<InventoryItem> getItems(List<Integer> itemIds) {
        if (itemIds != null && !itemIds.isEmpty()) {
            return inventoryItemRepository.findByInventoryItemIdInAndDeletedDateIsNull(itemIds);
        }
        return inventoryItemRepository.findAllByDeletedDateIsNull();
    }

    public byte[] generateProductCatalogExcel(List<Integer> itemIds) throws IOException {
        List<InventoryItem> items = getItems(itemIds);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Product Catalog");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row headerRow = sheet.createRow(0);
            String[] columns = {"Item Code", "Item Name", "UOM", "HSN Code", "Tax Category", "GST Rate %", "Minimum Selling Price (INR)", "Selling Price (INR)"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (InventoryItem item : items) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(item.getItemCode() != null ? item.getItemCode() : "");
                row.createCell(1).setCellValue(item.getName() != null ? item.getName() : "");
                row.createCell(2).setCellValue(item.getUom() != null ? item.getUom().name() : "");
                row.createCell(3).setCellValue(item.getHsnCode() != null ? item.getHsnCode() : "");

                if (item.getProductFinanceSettings() != null) {
                    row.createCell(4).setCellValue(item.getProductFinanceSettings().getTaxCategory() != null ? item.getProductFinanceSettings().getTaxCategory() : "");
                    row.createCell(5).setCellValue(item.getProductFinanceSettings().getGstRate() != null ? item.getProductFinanceSettings().getGstRate() : 0.0);
                    row.createCell(6).setCellValue(item.getProductFinanceSettings().getMinimumSellingPrice() != null ? item.getProductFinanceSettings().getMinimumSellingPrice() : 0.0);
                    row.createCell(7).setCellValue(item.getProductFinanceSettings().getSellingPrice() != null ? item.getProductFinanceSettings().getSellingPrice() : 0.0);
                } else {
                    row.createCell(4).setCellValue("");
                    row.createCell(5).setCellValue(0.0);
                    row.createCell(6).setCellValue(0.0);
                    row.createCell(7).setCellValue(0.0);
                }
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] generateBulkItemExportExcel(List<Integer> itemIds) throws IOException {
        List<InventoryItem> items = getItems(itemIds);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Bulk Item Export");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row headerRow = sheet.createRow(0);
            String[] columns = {
                    "Item Code", "Name", "HSN Code", "UOM", "Item Type", "Revision", "Remarks", "Item Group Code",
                    "Dimension", "Size", "Weight", "Basic Material", "Process Type", "Drawing Number",
                    "Reorder Level", "Min Stock", "Max Stock", "Lead Time", "Is Batch Tracked", "Is Serial Tracked", "Purchased", "Manufactured",
                    "Standard Cost", "Last Purchase Cost", "Selling Price", "Costing Method", "Profit Margin", "Minimum Selling Price", "Tax Category", "GST Rate %", "Currency"
            };

            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (InventoryItem item : items) {
                Row row = sheet.createRow(rowIdx++);
                int colIdx = 0;

                row.createCell(colIdx++).setCellValue(item.getItemCode() != null ? item.getItemCode() : "");
                row.createCell(colIdx++).setCellValue(item.getName() != null ? item.getName() : "");
                row.createCell(colIdx++).setCellValue(item.getHsnCode() != null ? item.getHsnCode() : "");
                row.createCell(colIdx++).setCellValue(item.getUom() != null ? item.getUom().name() : "");
                row.createCell(colIdx++).setCellValue(item.getItemType() != null ? item.getItemType().name() : "");
                row.createCell(colIdx++).setCellValue(item.getRevision());
                row.createCell(colIdx++).setCellValue(item.getRemarks() != null ? item.getRemarks() : "");
                row.createCell(colIdx++).setCellValue(item.getItemGroupCode() != null ? item.getItemGroupCode() : "");

                if (item.getProductSpecification() != null) {
                    row.createCell(colIdx++).setCellValue(item.getProductSpecification().getDimension() != null ? item.getProductSpecification().getDimension() : "");
                    row.createCell(colIdx++).setCellValue(item.getProductSpecification().getSize() != null ? item.getProductSpecification().getSize() : "");
                    row.createCell(colIdx++).setCellValue(item.getProductSpecification().getWeight() != null ? item.getProductSpecification().getWeight() : "");
                    row.createCell(colIdx++).setCellValue(item.getProductSpecification().getBasicMaterial() != null ? item.getProductSpecification().getBasicMaterial() : "");
                    row.createCell(colIdx++).setCellValue(item.getProductSpecification().getProcessType() != null ? item.getProductSpecification().getProcessType() : "");
                    row.createCell(colIdx++).setCellValue(item.getProductSpecification().getDrawingNumber() != null ? item.getProductSpecification().getDrawingNumber() : "");
                } else {
                    colIdx += 6;
                }

                if (item.getProductInventorySettings() != null) {
                    row.createCell(colIdx++).setCellValue(item.getProductInventorySettings().getReorderLevel());
                    row.createCell(colIdx++).setCellValue(item.getProductInventorySettings().getMinStock());
                    row.createCell(colIdx++).setCellValue(item.getProductInventorySettings().getMaxStock());
                    row.createCell(colIdx++).setCellValue(item.getProductInventorySettings().getLeadTime());
                    row.createCell(colIdx++).setCellValue(item.getProductInventorySettings().isBatchTracked());
                    row.createCell(colIdx++).setCellValue(item.getProductInventorySettings().isSerialTracked());
                    row.createCell(colIdx++).setCellValue(item.getProductInventorySettings().isPurchased());
                    row.createCell(colIdx++).setCellValue(item.getProductInventorySettings().isManufactured());
                } else {
                    colIdx += 8;
                }

                if (item.getProductFinanceSettings() != null) {
                    row.createCell(colIdx++).setCellValue(item.getProductFinanceSettings().getStandardCost() != null ? item.getProductFinanceSettings().getStandardCost() : 0.0);
                    row.createCell(colIdx++).setCellValue(item.getProductFinanceSettings().getLastPurchaseCost() != null ? item.getProductFinanceSettings().getLastPurchaseCost() : 0.0);
                    row.createCell(colIdx++).setCellValue(item.getProductFinanceSettings().getSellingPrice() != null ? item.getProductFinanceSettings().getSellingPrice() : 0.0);
                    row.createCell(colIdx++).setCellValue(item.getProductFinanceSettings().getCostingMethod() != null ? item.getProductFinanceSettings().getCostingMethod() : "");
                    row.createCell(colIdx++).setCellValue(item.getProductFinanceSettings().getProfitMargin() != null ? item.getProductFinanceSettings().getProfitMargin() : 0.0);
                    row.createCell(colIdx++).setCellValue(item.getProductFinanceSettings().getMinimumSellingPrice() != null ? item.getProductFinanceSettings().getMinimumSellingPrice() : 0.0);
                    row.createCell(colIdx++).setCellValue(item.getProductFinanceSettings().getTaxCategory() != null ? item.getProductFinanceSettings().getTaxCategory() : "");
                    row.createCell(colIdx++).setCellValue(item.getProductFinanceSettings().getGstRate() != null ? item.getProductFinanceSettings().getGstRate() : 0.0);
                    row.createCell(colIdx++).setCellValue(item.getProductFinanceSettings().getCurrency() != null ? item.getProductFinanceSettings().getCurrency() : "");
                } else {
                    colIdx += 9;
                }
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] generateProductMasterDataSheetPdf(List<Integer> itemIds) throws Exception {
        List<InventoryItem> items = getItems(itemIds);

        Context context = new Context();
        context.setVariable("items", items);
        context.setVariable("exportService", this);

        String html = templateEngine.process("product_master_data_sheet", context);

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        }
    }

    public byte[] generateVendorPriceComparisonExcel(List<Integer> itemIds) throws IOException {
        List<InventoryItem> items = getItems(itemIds);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Vendor Prices");
            String[] columns = {
                    "Item Code", "Item Name", "Supplier", "Supplier GSTIN", "Price Type", "Price Per Unit",
                    "Currency", "GST Registered", "Lead Time Days", "MOQ", "Last Purchased Date",
                    "Last Quoted Date", "Preferred Vendor", "Payment Terms"
            };
            writeHeader(workbook, sheet, columns);

            int rowIdx = 1;
            for (InventoryItem item : items) {
                for (ItemVendorPrice price : itemVendorPriceRepository.findByInventoryItem_InventoryItemIdAndDeletedDateIsNull(item.getInventoryItemId())) {
                    Row row = sheet.createRow(rowIdx++);
                    int col = 0;
                    row.createCell(col++).setCellValue(value(item.getItemCode()));
                    row.createCell(col++).setCellValue(value(item.getName()));
                    row.createCell(col++).setCellValue(price.getVendor() != null ? value(price.getVendor().getCompanyName()) : "");
                    row.createCell(col++).setCellValue(price.getVendor() != null ? value(price.getVendor().getGstNumber()) : "");
                    row.createCell(col++).setCellValue(price.getPriceType() != null ? price.getPriceType().name() : "");
                    row.createCell(col++).setCellValue(decimal(price.getPricePerUnit()));
                    row.createCell(col++).setCellValue(value(price.getCurrency()));
                    row.createCell(col++).setCellValue(price.isGstRegistered());
                    row.createCell(col++).setCellValue(price.getLeadTimeDays() != null ? price.getLeadTimeDays() : 0);
                    row.createCell(col++).setCellValue(decimal(price.getMinimumOrderQuantity()));
                    LocalDate lastPurchasedDate = price.getVendor() != null
                            ? goodsReceiptItemRepository.findLastPurchasedDate(item.getInventoryItemId(), price.getVendor().getId())
                            : null;
                    row.createCell(col++).setCellValue(lastPurchasedDate != null ? lastPurchasedDate.toString() : "");
                    row.createCell(col++).setCellValue(price.getLastQuotedDate() != null ? price.getLastQuotedDate().toString() : "");
                    row.createCell(col++).setCellValue(price.isPreferredVendor());
                    row.createCell(col).setCellValue(value(price.getPaymentTerms()));
                }
            }
            autosize(sheet, columns.length);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] generateEWayBillTallyExcel(List<Integer> itemIds) throws IOException {
        List<InventoryItem> items = getItems(itemIds);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("GST Import");
            String[] columns = {
                    "GSTIN", "Supplier Name", "Item Code", "Item Name", "HSN Code", "GST Rate %",
                    "UOM", "Quantity", "Unit Value", "Taxable Value", "CGST Rate %", "SGST Rate %", "IGST Rate %"
            };
            writeHeader(workbook, sheet, columns);

            int rowIdx = 1;
            for (InventoryItem item : items) {
                ItemVendorPrice preferred = itemVendorPriceRepository
                        .findByInventoryItem_InventoryItemIdAndPriceTypeAndIsPreferredVendorTrueAndDeletedDateIsNull(item.getInventoryItemId(), PriceType.PURCHASE)
                        .orElse(null);
                Row row = sheet.createRow(rowIdx++);
                double unitValue = preferred != null ? decimal(preferred.getPricePerUnit()) : lastPurchaseCost(item);
                double gstRate = gstRate(item);
                int col = 0;
                row.createCell(col++).setCellValue(preferred != null && preferred.getVendor() != null ? value(preferred.getVendor().getGstNumber()) : "");
                row.createCell(col++).setCellValue(preferred != null && preferred.getVendor() != null ? value(preferred.getVendor().getCompanyName()) : "");
                row.createCell(col++).setCellValue(value(item.getItemCode()));
                row.createCell(col++).setCellValue(value(item.getName()));
                row.createCell(col++).setCellValue(value(item.getHsnCode()));
                row.createCell(col++).setCellValue(gstRate);
                row.createCell(col++).setCellValue(item.getUom() != null ? item.getUom().name() : "");
                row.createCell(col++).setCellValue(1.0);
                row.createCell(col++).setCellValue(unitValue);
                row.createCell(col++).setCellValue(unitValue);
                row.createCell(col++).setCellValue(gstRate / 2);
                row.createCell(col++).setCellValue(gstRate / 2);
                row.createCell(col).setCellValue(gstRate);
            }
            autosize(sheet, columns.length);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] generateLowStockIndentExcel() throws IOException {
        List<InventoryItem> items = inventoryItemRepository.findAllByDeletedDateIsNull().stream()
                .filter(item -> item.getProductInventorySettings() != null)
                .filter(item -> item.getProductInventorySettings().getAvailableQuantity() < item.getProductInventorySettings().getReorderLevel())
                .toList();

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Low Stock Indent");
            String[] columns = {
                    "Item Code", "Item Name", "HSN Code", "UOM", "Available Qty", "Reorder Level",
                    "Indent Qty", "Vendor Name", "Vendor GSTIN", "Last Price", "Last Purchased Date", "Lead Time Days"
            };
            writeHeader(workbook, sheet, columns);

            int rowIdx = 1;
            for (InventoryItem item : items) {
                ItemVendorPrice price = preferredOrCheapest(item.getInventoryItemId(), PriceType.PURCHASE);
                double reorderLevel = item.getProductInventorySettings().getReorderLevel();
                double available = item.getProductInventorySettings().getAvailableQuantity();
                Row row = sheet.createRow(rowIdx++);
                int col = 0;
                row.createCell(col++).setCellValue(value(item.getItemCode()));
                row.createCell(col++).setCellValue(value(item.getName()));
                row.createCell(col++).setCellValue(value(item.getHsnCode()));
                row.createCell(col++).setCellValue(item.getUom() != null ? item.getUom().name() : "");
                row.createCell(col++).setCellValue(available);
                row.createCell(col++).setCellValue(reorderLevel);
                row.createCell(col++).setCellValue(Math.max(reorderLevel - available, 0.0));
                row.createCell(col++).setCellValue(price != null && price.getVendor() != null ? value(price.getVendor().getCompanyName()) : "");
                row.createCell(col++).setCellValue(price != null && price.getVendor() != null ? value(price.getVendor().getGstNumber()) : "");
                row.createCell(col++).setCellValue(price != null ? decimal(price.getPricePerUnit()) : lastPurchaseCost(item));
                LocalDate lastPurchasedDate = price != null && price.getVendor() != null
                        ? goodsReceiptItemRepository.findLastPurchasedDate(item.getInventoryItemId(), price.getVendor().getId())
                        : null;
                row.createCell(col++).setCellValue(lastPurchasedDate != null ? lastPurchasedDate.toString() : "");
                row.createCell(col).setCellValue(price != null && price.getLeadTimeDays() != null ? price.getLeadTimeDays() : 0);
            }
            autosize(sheet, columns.length);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] generateJobWorkItemsExcel(List<Integer> itemIds) throws IOException {
        List<InventoryItem> items = getItems(itemIds).stream()
                .filter(item -> item.getItemType() == ItemType.SUB_CONTRACTED)
                .toList();

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Job Work Items");
            String[] columns = {
                    "Item Code", "Item Name", "HSN Code", "UOM", "Vendor Name", "Vendor GSTIN",
                    "Job Work Rate", "Currency", "Lead Time Days", "MOQ", "GST Rate %", "Preferred Vendor"
            };
            writeHeader(workbook, sheet, columns);

            int rowIdx = 1;
            for (InventoryItem item : items) {
                List<ItemVendorPrice> prices = itemVendorPriceRepository
                        .findByInventoryItem_InventoryItemIdAndPriceTypeAndDeletedDateIsNull(item.getInventoryItemId(), PriceType.JOB_WORK);
                if (prices.isEmpty()) {
                    rowIdx = writeJobWorkRow(sheet, rowIdx, item, null);
                } else {
                    for (ItemVendorPrice price : prices) {
                        rowIdx = writeJobWorkRow(sheet, rowIdx, item, price);
                    }
                }
            }
            autosize(sheet, columns.length);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public String itemPageUrl(InventoryItem item) {
        return frontendUrl.replaceAll("/+$", "") + "/inventory-items/" + item.getInventoryItemId();
    }

    public String qrCodeDataUri(InventoryItem item) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(itemPageUrl(item), BarcodeFormat.QR_CODE, 120, 120);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    private int writeJobWorkRow(Sheet sheet, int rowIdx, InventoryItem item, ItemVendorPrice price) {
        Row row = sheet.createRow(rowIdx++);
        int col = 0;
        row.createCell(col++).setCellValue(value(item.getItemCode()));
        row.createCell(col++).setCellValue(value(item.getName()));
        row.createCell(col++).setCellValue(value(item.getHsnCode()));
        row.createCell(col++).setCellValue(item.getUom() != null ? item.getUom().name() : "");
        row.createCell(col++).setCellValue(price != null && price.getVendor() != null ? value(price.getVendor().getCompanyName()) : "");
        row.createCell(col++).setCellValue(price != null && price.getVendor() != null ? value(price.getVendor().getGstNumber()) : "");
        row.createCell(col++).setCellValue(price != null ? decimal(price.getPricePerUnit()) : 0.0);
        row.createCell(col++).setCellValue(price != null ? value(price.getCurrency()) : "");
        row.createCell(col++).setCellValue(price != null && price.getLeadTimeDays() != null ? price.getLeadTimeDays() : 0);
        row.createCell(col++).setCellValue(price != null ? decimal(price.getMinimumOrderQuantity()) : 0.0);
        row.createCell(col++).setCellValue(gstRate(item));
        row.createCell(col).setCellValue(price != null && price.isPreferredVendor());
        return rowIdx;
    }

    private ItemVendorPrice preferredOrCheapest(int itemId, PriceType priceType) {
        return itemVendorPriceRepository
                .findByInventoryItem_InventoryItemIdAndPriceTypeAndIsPreferredVendorTrueAndDeletedDateIsNull(itemId, priceType)
                .orElseGet(() -> itemVendorPriceRepository.findCheapestByItemAndType(itemId, priceType).stream().findFirst().orElse(null));
    }

    private void writeHeader(Workbook workbook, Sheet sheet, String[] columns) {
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < columns.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void autosize(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private String value(String value) {
        return value != null ? value : "";
    }

    private double decimal(BigDecimal value) {
        return value != null ? value.doubleValue() : 0.0;
    }

    private double gstRate(InventoryItem item) {
        return item.getProductFinanceSettings() != null && item.getProductFinanceSettings().getGstRate() != null
                ? item.getProductFinanceSettings().getGstRate()
                : 0.0;
    }

    private double lastPurchaseCost(InventoryItem item) {
        return item.getProductFinanceSettings() != null && item.getProductFinanceSettings().getLastPurchaseCost() != null
                ? item.getProductFinanceSettings().getLastPurchaseCost()
                : 0.0;
    }
}
