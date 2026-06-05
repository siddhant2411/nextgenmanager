package com.nextgenmanager.nextgenmanager.marketing.enquiry.service;

import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiredProducts;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.Enquiry;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryStatus;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.repository.EnquiryRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EnquiryExportService {

    @Autowired
    private EnquiryRepository enquiryRepository;

    private static final Logger logger = LoggerFactory.getLogger(EnquiryExportService.class);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");

    private static class StyleCache {
        private final XSSFWorkbook wb;
        private final Map<String, XSSFCellStyle> cache = new HashMap<>();

        StyleCache(XSSFWorkbook wb) {
            this.wb = wb;
        }

        XSSFCellStyle get(String key, java.util.function.Consumer<XSSFCellStyle> initializer) {
            return cache.computeIfAbsent(key, k -> {
                XSSFCellStyle s = wb.createCellStyle();
                initializer.accept(s);
                return s;
            });
        }
    }

    @Transactional(readOnly = true)
    public byte[] exportToExcel(String enqNo, String companyName, LocalDate lastContactedDate,
                                LocalDate enqDate, LocalDate closedDate, Integer daysForNextFollowup,
                                String lastContactedDateComp, String enqDateComp, String closedDateComp) throws Exception {

        logger.debug("Starting Excel export for enquiry. Params: enqNo={}, companyName={}", enqNo, companyName);

        Page<Object[]> rawPage = enquiryRepository.getActiveEnquiries(
                PageRequest.of(0, 5000, Sort.by("enqDate").descending()),
                enqNo, companyName, lastContactedDate, daysForNextFollowup,
                enqDate, closedDate,
                lastContactedDateComp != null ? lastContactedDateComp : "=",
                enqDateComp != null ? enqDateComp : "=",
                closedDateComp != null ? closedDateComp : ">"
        );

        List<Long> ids = rawPage.getContent().stream()
                .map(r -> ((Number) r[0]).longValue())
                .collect(Collectors.toList());

        logger.debug("Found {} enquiries for export after filtering.", ids.size());

        if (ids.isEmpty()) {
            logger.warn("No enquiries found for the given filters. Returning empty sheet.");
            try (XSSFWorkbook wb = new XSSFWorkbook()) {
                wb.createSheet("No Data Found");
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                wb.write(out);
                return out.toByteArray();
            }
        }

        List<Enquiry> enquiries = enquiryRepository.findAllById(ids);
        Map<Long, Integer> orderMap = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) orderMap.put(ids.get(i), i);
        enquiries.sort(Comparator.comparingInt(e -> orderMap.getOrDefault(e.getId(), 999)));

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            StyleCache styles = new StyleCache(wb);
            logger.debug("Building Sheet 1: Enquiry Register");
            buildSheet1(wb, enquiries, styles);
            logger.debug("Building Sheet 2: Item-wise Detail");
            buildSheet2(wb, enquiries, styles);
            logger.debug("Building Sheet 3: Summary");
            buildSheet3(wb, enquiries, styles);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            byte[] bytes = out.toByteArray();
            logger.debug("Excel export completed successfully. Byte array length: {}", bytes.length);
            return bytes;
        }
    }

    private void buildSheet1(XSSFWorkbook wb, List<Enquiry> enquiries, StyleCache styles) {
        XSSFSheet sheet = wb.createSheet("Enquiry Register");
        sheet.setDefaultColumnWidth(18);

        XSSFCellStyle headerStyle = getHeaderStyle(styles);
        XSSFCellStyle titleStyle = getTitleStyle(styles);
        XSSFCellStyle subTitleStyle = getSubTitleStyle(styles);
        XSSFCellStyle currencyStyle = getCurrencyStyle(styles);
        XSSFCellStyle normalStyle = getNormalStyle(styles);
        XSSFCellStyle altRowStyle = getAltRowStyle(styles);

        Row title = sheet.createRow(0);
        title.setHeightInPoints(25);
        Cell tc = title.createCell(0);
        tc.setCellValue("ENQUIRY REGISTER");
        tc.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 20));

        Row sub = sheet.createRow(1);
        Cell sc = sub.createCell(0);
        sc.setCellValue("Generated: " + LocalDate.now().format(DATE_FMT) + "   |   Total Records: " + enquiries.size());
        sc.setCellStyle(subTitleStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 20));

        String[] headers = {
            "#", "Enquiry No", "Date", "Company", "Contact Person", "Phone", "Email",
            "City", "State", "Opportunity / Requirement", "Type", "Source",
            "Priority", "Status", "Expected Value (₹)", "Probability (%)",
            "Assigned To", "Last Contacted", "Next Follow-up", "Target Close", "Remarks"
        };
        Row hRow = sheet.createRow(3);
        for (int i = 0; i < headers.length; i++) {
            Cell c = hRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }

        LocalDate today = LocalDate.now();
        for (int i = 0; i < enquiries.size(); i++) {
            Enquiry e = enquiries.get(i);
            Row row = sheet.createRow(4 + i);
            boolean isAlt = i % 2 != 0;
            XSSFCellStyle rowStyle = isAlt ? altRowStyle : normalStyle;

            setCell(row, 0, String.valueOf(i + 1), rowStyle);
            setCell(row, 1, e.getEnqNo(), rowStyle);
            setCell(row, 2, fmt(e.getEnqDate()), rowStyle);
            setCell(row, 3, e.getContact() != null ? e.getContact().getCompanyName() : (e.getManualCompanyName() != null ? e.getManualCompanyName() : ""), rowStyle);
            setCell(row, 4, e.getContactPersonName(), rowStyle);
            setCell(row, 5, e.getContactPersonPhone(), rowStyle);
            setCell(row, 6, e.getContactPersonEmail(), rowStyle);
            setCell(row, 7, e.getCity(), rowStyle);
            setCell(row, 8, e.getState(), rowStyle);
            setCell(row, 9, e.getOpportunityName(), rowStyle);
            setCell(row, 10, e.getType() != null ? e.getType().name() : "", rowStyle);
            setCell(row, 11, e.getEnquirySource(), rowStyle);

            String prio = e.getPriority() != null ? e.getPriority().name() : "";
            Cell priCell = row.createCell(12);
            priCell.setCellValue(prio);
            priCell.setCellStyle(getPriorityStyle(styles, prio));

            EnquiryStatus status = e.getStatus();
            Cell stCell = row.createCell(13);
            stCell.setCellValue(status != null ? status.name() : "");
            stCell.setCellStyle(getStatusStyle(styles, status));

            Cell revCell = row.createCell(14);
            revCell.setCellValue(e.getExpectedRevenue() != null ? e.getExpectedRevenue().doubleValue() : 0);
            revCell.setCellStyle(isAlt ? getCurrencyStyleAlt(styles) : currencyStyle);

            setCell(row, 15, e.getProbability() != null ? e.getProbability() + "%" : "", rowStyle);
            setCell(row, 16, e.getAssignedTo() != null ? e.getAssignedTo().getUsername() : "Unassigned", rowStyle);
            setCell(row, 17, fmt(e.getLastContactedDate()), rowStyle);

            Cell fuCell = row.createCell(18);
            fuCell.setCellValue(fmt(e.getNextFollowupDate()));
            boolean overdue = e.getNextFollowupDate() != null && e.getNextFollowupDate().isBefore(today)
                    && e.getStatus() != EnquiryStatus.CONVERTED && e.getStatus() != EnquiryStatus.LOST
                    && e.getStatus() != EnquiryStatus.CLOSED;
            fuCell.setCellStyle(overdue ? getOverdueStyle(styles, isAlt) : rowStyle);

            setCell(row, 19, fmt(e.getTargetCloseDate()), rowStyle);
            setCell(row, 20, e.getFollowupRemarks(), rowStyle);
        }

        for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
        sheet.createFreezePane(0, 4);
    }

    private void buildSheet2(XSSFWorkbook wb, List<Enquiry> enquiries, StyleCache styles) {
        XSSFSheet sheet = wb.createSheet("Item-wise Detail");
        XSSFCellStyle headerStyle = getHeaderStyle(styles);
        XSSFCellStyle normalStyle = getNormalStyle(styles);
        XSSFCellStyle altRowStyle  = getAltRowStyle(styles);
        XSSFCellStyle currencyStyle = getCurrencyStyle(styles);

        Row title = sheet.createRow(0);
        Cell tc = title.createCell(0);
        tc.setCellValue("ITEM-WISE ENQUIRY DETAILS");
        tc.setCellStyle(getTitleStyle(styles));
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 12));

        String[] headers = {
            "#", "Enquiry No", "Date", "Company", "Opportunity",
            "Item Code", "Item Description", "Qty", "UOM",
            "Unit Price (₹)", "Total Value (₹)", "Special Instructions", "Status"
        };
        Row hRow = sheet.createRow(2);
        for (int i = 0; i < headers.length; i++) {
            Cell c = hRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }

        int rowIdx = 3;
        int sno = 1;
        for (Enquiry e : enquiries) {
            List<EnquiredProducts> products = e.getEnquiredProducts();
            boolean hasItems = products != null && !products.isEmpty();
            int count = hasItems ? products.size() : 1;

            for (int j = 0; j < count; j++) {
                Row row = sheet.createRow(rowIdx++);
                boolean isAlt = sno % 2 != 0;
                XSSFCellStyle rs = isAlt ? altRowStyle : normalStyle;

                setCell(row, 0, String.valueOf(sno++), rs);
                setCell(row, 1, e.getEnqNo(), rs);
                setCell(row, 2, fmt(e.getEnqDate()), rs);
                setCell(row, 3, e.getContact() != null ? e.getContact().getCompanyName() : (e.getManualCompanyName() != null ? e.getManualCompanyName() : ""), rs);
                setCell(row, 4, e.getOpportunityName(), rs);

                if (hasItems) {
                    EnquiredProducts p = products.get(j);
                    setCell(row, 5, p.getInventoryItem() != null ? p.getInventoryItem().getItemCode() : "—", rs);
                    setCell(row, 6, p.getInventoryItem() != null ? p.getInventoryItem().getName() : p.getProductNameRequired(), rs);
                    row.createCell(7).setCellValue(p.getQty() != null ? p.getQty().doubleValue() : 0);
                    row.getCell(7).setCellStyle(rs);
                    setCell(row, 8, p.getInventoryItem() != null && p.getInventoryItem().getUom() != null ? p.getInventoryItem().getUom().name() : "", rs);
                    
                    Cell upCell = row.createCell(9);
                    upCell.setCellValue(p.getPricePerUnit() != null ? p.getPricePerUnit().doubleValue() : 0);
                    upCell.setCellStyle(isAlt ? getCurrencyStyleAlt(styles) : currencyStyle);

                    BigDecimal total = (p.getQty() != null && p.getPricePerUnit() != null) ? p.getQty().multiply(p.getPricePerUnit()) : BigDecimal.ZERO;
                    Cell totCell = row.createCell(10);
                    totCell.setCellValue(total.doubleValue());
                    totCell.setCellStyle(isAlt ? getCurrencyStyleAlt(styles) : currencyStyle);

                    setCell(row, 11, p.getSpecialInstruction(), rs);
                } else {
                    setCell(row, 5, "—", rs);
                    setCell(row, 6, "No items added", rs);
                    for (int k = 7; k <= 11; k++) setCell(row, k, "", rs);
                }
                setCell(row, 12, e.getStatus() != null ? e.getStatus().name() : "", rs);
            }
        }
        for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
        sheet.createFreezePane(0, 3);
    }

    private void buildSheet3(XSSFWorkbook wb, List<Enquiry> enquiries, StyleCache styles) {
        XSSFSheet sheet = wb.createSheet("Summary");
        Row r = sheet.createRow(0);
        r.createCell(0).setCellValue("SUMMARY");
        r.getCell(0).setCellStyle(getTitleStyle(styles));
        
        Row r1 = sheet.createRow(2); r1.createCell(0).setCellValue("Total Leads"); r1.createCell(1).setCellValue(enquiries.size());
        BigDecimal total = enquiries.stream().map(e -> e.getExpectedRevenue() != null ? e.getExpectedRevenue() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
        Row r2 = sheet.createRow(3); r2.createCell(0).setCellValue("Pipeline Value"); r2.createCell(1).setCellValue(total.doubleValue());
        r2.getCell(1).setCellStyle(getCurrencyStyle(styles));
        sheet.autoSizeColumn(0); sheet.autoSizeColumn(1);
    }

    private XSSFCellStyle getTitleStyle(StyleCache s) {
        return s.get("title", st -> {
            XSSFFont f = s.wb.createFont(); f.setBold(true); f.setFontHeightInPoints((short)16);
            f.setColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
            st.setFont(f); st.setAlignment(HorizontalAlignment.CENTER);
        });
    }

    private XSSFCellStyle getHeaderStyle(StyleCache s) {
        return s.get("header", st -> {
            st.setFillForegroundColor(IndexedColors.GREY_80_PERCENT.getIndex());
            st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            XSSFFont f = s.wb.createFont(); f.setBold(true); f.setColor(IndexedColors.WHITE.getIndex());
            st.setFont(f); st.setAlignment(HorizontalAlignment.CENTER); setBorders(st);
        });
    }

    private XSSFCellStyle getNormalStyle(StyleCache s) {
        return s.get("normal", st -> { setBorders(st); });
    }

    private XSSFCellStyle getAltRowStyle(StyleCache s) {
        return s.get("alt", st -> {
            st.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorders(st);
        });
    }

    private XSSFCellStyle getCurrencyStyle(StyleCache s) {
        return s.get("curr", st -> {
            st.setDataFormat(s.wb.createDataFormat().getFormat("#,##0.00"));
            setBorders(st); st.setAlignment(HorizontalAlignment.RIGHT);
        });
    }

    private XSSFCellStyle getCurrencyStyleAlt(StyleCache s) {
        return s.get("currAlt", st -> {
            st.setDataFormat(s.wb.createDataFormat().getFormat("#,##0.00"));
            st.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorders(st); st.setAlignment(HorizontalAlignment.RIGHT);
        });
    }

    private XSSFCellStyle getPriorityStyle(StyleCache s, String p) {
        return s.get("prio_" + p, st -> {
            short color = switch (p) {
                case "HOT" -> IndexedColors.RED.getIndex();
                case "WARM" -> IndexedColors.ORANGE.getIndex();
                case "COLD" -> IndexedColors.LIGHT_BLUE.getIndex();
                default -> IndexedColors.BLACK.getIndex();
            };
            XSSFFont f = s.wb.createFont(); f.setBold(true); f.setColor(color);
            st.setFont(f); st.setAlignment(HorizontalAlignment.CENTER); setBorders(st);
        });
    }

    private XSSFCellStyle getStatusStyle(StyleCache s, EnquiryStatus st) {
        String name = st != null ? st.name() : "NULL";
        return s.get("status_" + name, style -> {
            short color = (st == null) ? IndexedColors.BLACK.getIndex() : switch (st) {
                case CONVERTED -> IndexedColors.GREEN.getIndex();
                case LOST, JUNK -> IndexedColors.RED.getIndex();
                default -> IndexedColors.ROYAL_BLUE.getIndex();
            };
            XSSFFont f = s.wb.createFont(); f.setBold(true); f.setColor(color);
            style.setFont(f); style.setAlignment(HorizontalAlignment.CENTER); setBorders(style);
        });
    }

    private XSSFCellStyle getOverdueStyle(StyleCache s, boolean isAlt) {
        return s.get("overdue_" + isAlt, st -> {
            st.setFillForegroundColor(IndexedColors.RED.getIndex());
            st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            XSSFFont f = s.wb.createFont(); f.setColor(IndexedColors.WHITE.getIndex()); f.setBold(true);
            st.setFont(f); setBorders(st);
        });
    }

    private XSSFCellStyle getSubTitleStyle(StyleCache s) {
        return s.get("subtitle", st -> {
            XSSFFont f = s.wb.createFont(); f.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            st.setFont(f); st.setAlignment(HorizontalAlignment.CENTER);
        });
    }

    private void setBorders(CellStyle s) {
        s.setBorderBottom(BorderStyle.THIN); s.setBottomBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
        s.setBorderRight(BorderStyle.THIN);  s.setRightBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
    }

    private void setCell(Row row, int col, String val, CellStyle s) {
        Cell c = row.createCell(col); 
        c.setCellValue(sanitize(val)); 
        c.setCellStyle(s);
    }

    private String sanitize(String val) {
        if (val == null) return "";
        // Remove control characters (0x00-0x08, 0x0B-0x0C, 0x0E-0x1F) which are invalid in XML 1.0
        return val.replaceAll("[\\x00-\\x08\\x0B-\\x0C\\x0E-\\x1F]", "");
    }

    private String fmt(LocalDate date) { return date != null ? date.format(DATE_FMT) : ""; }
}
