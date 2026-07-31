package com.nextgenmanager.nextgenmanager.items.service;

import com.nextgenmanager.nextgenmanager.bom.dto.BomCostBreakdownDTO;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.repository.BomRepository;
import com.nextgenmanager.nextgenmanager.bom.service.BomService;
import com.nextgenmanager.nextgenmanager.items.DTO.ItemPriceDTO;
import com.nextgenmanager.nextgenmanager.items.DTO.PriceListExportRequest;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ItemType;
import com.nextgenmanager.nextgenmanager.items.model.ProductFinanceSettings;
import com.nextgenmanager.nextgenmanager.items.model.UOM;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the price picture the export builds: every cost and price the item master holds, the
 * BOM-loaded selling cost, and the fact that a missing floor no longer leaves the internal sheet
 * without a way to judge a price.
 */
@ExtendWith(MockitoExtension.class)
class PriceListExportServiceTest {

    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private InventoryItemService inventoryItemService;
    @Mock private BomRepository bomRepository;
    @Mock private BomService bomService;

    private PriceListExportService service;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);

        service = new PriceListExportService(inventoryItemRepository, inventoryItemService,
                bomRepository, bomService, engine);
    }

    // ── fixtures ──

    private InventoryItem item(int id, String code, Double standardCost, Double sellingPrice,
                               Double minimumSellingPrice, Double lastPurchaseCost, Double gstRate) {
        InventoryItem item = new InventoryItem();
        item.setInventoryItemId(id);
        item.setItemCode(code);
        item.setName("Item " + code);
        item.setUom(UOM.NOS);
        item.setItemType(ItemType.FINISHED_GOOD);
        item.setHsnCode("7318");

        ProductFinanceSettings fin = new ProductFinanceSettings();
        fin.setStandardCost(standardCost);
        fin.setSellingPrice(sellingPrice);
        fin.setMinimumSellingPrice(minimumSellingPrice);
        fin.setLastPurchaseCost(lastPurchaseCost);
        fin.setGstRate(gstRate);
        item.setProductFinanceSettings(fin);
        return item;
    }

    private Bom activeBom(int bomId, InventoryItem parent) {
        Bom bom = new Bom();
        bom.setId(bomId);
        bom.setParentInventoryItem(parent);
        return bom;
    }

    private BomCostBreakdownDTO breakdown(String totalCost) {
        BomCostBreakdownDTO dto = new BomCostBreakdownDTO();
        dto.setTotalCost(new BigDecimal(totalCost));
        return dto;
    }

    private PriceListExportRequest request(String audience, Integer... itemIds) {
        PriceListExportRequest request = new PriceListExportRequest();
        request.setAudience(audience);
        request.setItemIds(List.of(itemIds));
        return request;
    }

    // ── tests ──

    @Test
    void internalRow_carriesEveryCostAndPrice_andUsesLoadedBomCostForMargin() {
        InventoryItem widget = item(1, "W-100", 100d, 200d, 160d, 92.5d, 18d);
        when(inventoryItemRepository.findByInventoryItemIdInAndDeletedDateIsNull(List.of(1)))
                .thenReturn(List.of(widget));
        when(bomRepository.findActiveBomsByParentItemIds(anyList()))
                .thenReturn(List.of(activeBom(77, widget)));
        when(bomService.getBomCostBreakdown(77)).thenReturn(breakdown("120.00"));

        List<ItemPriceDTO> rows = service.buildRows(request("INTERNAL", 1));

        assertThat(rows).hasSize(1);
        ItemPriceDTO row = rows.get(0);
        assertThat(row.getStandardCost()).isEqualTo(100d);
        assertThat(row.getSellingCost()).isEqualTo(120d);          // BOM total, incl. overhead
        assertThat(row.isSellingCostFromBom()).isTrue();
        assertThat(row.getLastPurchaseCost()).isEqualTo(92.5d);
        assertThat(row.getListPrice()).isEqualTo(200d);
        assertThat(row.getFloorPrice()).isEqualTo(160d);
        assertThat(row.getMaxDiscountPercent()).isEqualTo(20d);    // (200-160)/200
        assertThat(row.getMarginPercent()).isEqualTo(40d);         // (200-120)/200
        assertThat(row.getGstRate()).isEqualTo(18d);
        assertThat(row.getPriceInclGst()).isEqualTo(236d);
        assertThat(row.isCostMissing()).isFalse();
        assertThat(row.isFloorMissing()).isFalse();
    }

    @Test
    void noFloorConfigured_stillReportsCostAndMargin_andInventsNoFloor() {
        InventoryItem widget = item(2, "W-200", 80d, 100d, null, null, 18d);
        when(inventoryItemRepository.findByInventoryItemIdInAndDeletedDateIsNull(List.of(2)))
                .thenReturn(List.of(widget));
        when(bomRepository.findActiveBomsByParentItemIds(anyList())).thenReturn(List.of());

        ItemPriceDTO row = service.buildRows(request("INTERNAL", 2)).get(0);

        assertThat(row.getFloorPrice()).isNull();
        assertThat(row.isFloorMissing()).isTrue();
        assertThat(row.getMaxDiscountPercent()).isNull();
        // Cost is what makes the row usable without a floor.
        assertThat(row.getSellingCost()).isEqualTo(80d);
        assertThat(row.isSellingCostFromBom()).isFalse();          // no active BOM -> standard cost
        assertThat(row.getMarginPercent()).isEqualTo(20d);
    }

    @Test
    void listPriceBelowCost_yieldsNegativeMargin() {
        InventoryItem widget = item(3, "W-300", 150d, 100d, null, null, null);
        when(inventoryItemRepository.findByInventoryItemIdInAndDeletedDateIsNull(List.of(3)))
                .thenReturn(List.of(widget));
        when(bomRepository.findActiveBomsByParentItemIds(anyList())).thenReturn(List.of());

        assertThat(service.buildRows(request("INTERNAL", 3)).get(0).getMarginPercent()).isEqualTo(-50d);
    }

    @Test
    void customerExport_doesNotCostBoms() {
        InventoryItem widget = item(4, "W-400", 100d, 200d, 160d, null, 18d);
        when(inventoryItemRepository.findByInventoryItemIdInAndDeletedDateIsNull(List.of(4)))
                .thenReturn(List.of(widget));

        List<ItemPriceDTO> rows = service.buildRows(request("CUSTOMER", 4));

        assertThat(rows).hasSize(1);
        verify(bomRepository, never()).findActiveBomsByParentItemIds(anyList());
        verify(bomService, never()).getBomCostBreakdown(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void unpricedItemsAreSkipped() {
        when(inventoryItemRepository.findByInventoryItemIdInAndDeletedDateIsNull(List.of(5)))
                .thenReturn(List.of(item(5, "W-500", 100d, null, null, null, 18d)));

        assertThat(service.buildRows(request("CUSTOMER", 5))).isEmpty();
    }

    @Test
    void uncostableBom_fallsBackToStandardCostInsteadOfFailing() {
        InventoryItem widget = item(6, "W-600", 90d, 150d, null, null, 18d);
        when(inventoryItemRepository.findByInventoryItemIdInAndDeletedDateIsNull(List.of(6)))
                .thenReturn(List.of(widget));
        when(bomRepository.findActiveBomsByParentItemIds(anyList()))
                .thenReturn(List.of(activeBom(99, widget)));
        when(bomService.getBomCostBreakdown(99)).thenThrow(new IllegalStateException("bad BOM"));

        ItemPriceDTO row = service.buildRows(request("INTERNAL", 6)).get(0);

        assertThat(row.getSellingCost()).isEqualTo(90d);
        assertThat(row.isSellingCostFromBom()).isFalse();
    }

    @Test
    void internalPdfRendersWithTheCostColumns() throws Exception {
        InventoryItem widget = item(7, "W-700", 100d, 200d, null, null, 18d);
        when(inventoryItemRepository.findByInventoryItemIdInAndDeletedDateIsNull(List.of(7)))
                .thenReturn(List.of(widget));
        when(bomRepository.findActiveBomsByParentItemIds(anyList())).thenReturn(List.of());

        byte[] pdf = service.generatePdf(request("INTERNAL", 7));

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        // The cost columns only fit landscape; a portrait internal sheet would clip them.
        try (PDDocument doc = PDDocument.load(pdf)) {
            PDRectangle page = doc.getPage(0).getMediaBox();
            assertThat(page.getWidth()).isGreaterThan(page.getHeight());
        }
    }

    @Test
    void customerPdfStaysPortrait() throws Exception {
        InventoryItem widget = item(10, "W-1000", 100d, 200d, null, null, 18d);
        when(inventoryItemRepository.findByInventoryItemIdInAndDeletedDateIsNull(List.of(10)))
                .thenReturn(List.of(widget));

        try (PDDocument doc = PDDocument.load(service.generatePdf(request("CUSTOMER", 10)))) {
            PDRectangle page = doc.getPage(0).getMediaBox();
            assertThat(page.getWidth()).isLessThan(page.getHeight());
        }
    }

    @Test
    void internalExcelCarriesTheCostColumns() throws Exception {
        InventoryItem widget = item(8, "W-800", 100d, 200d, 160d, 92.5d, 18d);
        when(inventoryItemRepository.findByInventoryItemIdInAndDeletedDateIsNull(List.of(8)))
                .thenReturn(List.of(widget));
        when(bomRepository.findActiveBomsByParentItemIds(anyList()))
                .thenReturn(List.of(activeBom(88, widget)));
        when(bomService.getBomCostBreakdown(88)).thenReturn(breakdown("120.00"));

        byte[] xlsx = service.generateExcel(request("INTERNAL", 8));

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(3);
            assertThat(header.getCell(5).getStringCellValue()).isEqualTo("Standard Cost");
            assertThat(header.getCell(6).getStringCellValue()).isEqualTo("Selling Cost");
            assertThat(header.getCell(7).getStringCellValue()).isEqualTo("Last Purchase Cost");
            assertThat(header.getCell(8).getStringCellValue()).isEqualTo("List Price");
            assertThat(header.getCell(9).getStringCellValue()).isEqualTo("Margin %");
            assertThat(header.getCell(10).getStringCellValue()).isEqualTo("Floor Price");

            Row data = sheet.getRow(4);
            assertThat(data.getCell(5).getNumericCellValue()).isEqualTo(100d);
            assertThat(data.getCell(6).getNumericCellValue()).isEqualTo(120d);
            assertThat(data.getCell(7).getNumericCellValue()).isEqualTo(92.5d);
            assertThat(data.getCell(8).getNumericCellValue()).isEqualTo(200d);
            assertThat(data.getCell(9).getNumericCellValue()).isEqualTo(40d);
            assertThat(data.getCell(10).getNumericCellValue()).isEqualTo(160d);
            assertThat(data.getCell(13).getNumericCellValue()).isEqualTo(236d);
        }
    }

    @Test
    void customerExcelKeepsCostOut() throws Exception {
        InventoryItem widget = item(9, "W-900", 100d, 200d, 160d, null, 18d);
        when(inventoryItemRepository.findByInventoryItemIdInAndDeletedDateIsNull(List.of(9)))
                .thenReturn(List.of(widget));

        byte[] xlsx = service.generateExcel(request("CUSTOMER", 9));

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Row header = wb.getSheetAt(0).getRow(3);
            assertThat(header.getLastCellNum()).isEqualTo((short) 8);
            assertThat(header.getCell(5).getStringCellValue()).isEqualTo("List Price");
        }
    }
}
