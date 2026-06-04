package com.nextgenmanager.nextgenmanager.marketing.enquiry.service;

import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.contact.repository.ContactRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.BulkImportResultDTO;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.*;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.repository.EnquiryRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class EnquiryImportService {

    private static final Logger logger = LoggerFactory.getLogger(EnquiryImportService.class);

    // Column order for both Excel and CSV templates
    // 0:enqNo(optional), 1:opportunityName*, 2:companyName, 3:contactPersonName,
    // 4:contactPersonPhone, 5:contactPersonEmail, 6:city, 7:state,
    // 8:enquirySource, 9:referenceNumber, 10:expectedRevenue,
    // 11:probability(0-100), 12:priority(HOT/WARM/COLD),
    // 13:status(NEW/CONTACTED/etc), 14:enqDate(yyyy-MM-dd),
    // 15:nextFollowupDate(yyyy-MM-dd),
    // 16:item1, 17:item1_qty, 18:item2, 19:item2_qty, ... (pairs, up to item15)
    private static final int ITEM_START_COL = 16;
    private static final int MAX_ITEMS = 15;

    private record ItemEntry(String value, BigDecimal qty) {}

    @Autowired private EnquiryRepository enquiryRepository;
    @Autowired private ContactRepository contactRepository;
    @Autowired private InventoryItemRepository inventoryItemRepository;
    @Autowired private EnquiryNumberGenerator enquiryNumberGenerator;

    @Transactional
    public BulkImportResultDTO importFromFile(MultipartFile file) throws Exception {
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        String contentType = file.getContentType() != null ? file.getContentType() : "";
        if (name.endsWith(".xlsx") || name.endsWith(".xls") || contentType.contains("spreadsheet") || contentType.contains("excel")) {
            return importFromExcel(file);
        }
        return importFromCsv(file);
    }

    private BulkImportResultDTO importFromExcel(MultipartFile file) throws Exception {
        List<String> errors = new ArrayList<>();
        int created = 0, duplicates = 0, skipped = 0;

        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row)) continue;
                try {
                    List<ItemEntry> items = new ArrayList<>();
                    for (int c = ITEM_START_COL; c < row.getLastCellNum(); c += 2) {
                        String v = getCellString(row, c);
                        if (v == null || v.isBlank()) continue;
                        String qtyStr = getCellString(row, c + 1);
                        items.add(new ItemEntry(v.trim(), parseBigDecimal(qtyStr, BigDecimal.ONE)));
                    }
                    Enquiry e = buildEnquiry(
                        getCellString(row, 0),
                        getCellString(row, 1), getCellString(row, 2), getCellString(row, 3),
                        getCellString(row, 4), getCellString(row, 5), getCellString(row, 6),
                        getCellString(row, 7), getCellString(row, 8), getCellString(row, 9),
                        getCellString(row, 10), getCellString(row, 11), getCellString(row, 12),
                        getCellString(row, 13), getCellString(row, 14), getCellString(row, 15),
                        items
                    );
                    enquiryRepository.save(e);
                    created++;
                } catch (DuplicateRowException ex) {
                    duplicates++;
                } catch (ImportRowException ex) {
                    errors.add("Row " + (i + 1) + ": " + ex.getMessage());
                    skipped++;
                }
            }
        }
        logger.info("Excel import complete: {} created, {} duplicates, {} skipped", created, duplicates, skipped);
        return new BulkImportResultDTO(created, duplicates, skipped, errors);
    }

    private BulkImportResultDTO importFromCsv(MultipartFile file) throws Exception {
        List<String> errors = new ArrayList<>();
        int created = 0, duplicates = 0, skipped = 0;
        int lineNum = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (lineNum == 1 || line.trim().isEmpty()) continue;
                String[] cols = parseCsvLine(line);
                List<ItemEntry> items = new ArrayList<>();
                for (int c = ITEM_START_COL; c < cols.length; c += 2) {
                    String v = col(cols, c);
                    if (v == null || v.isBlank()) continue;
                    String qtyStr = col(cols, c + 1);
                    items.add(new ItemEntry(v.trim(), parseBigDecimal(qtyStr, BigDecimal.ONE)));
                }
                try {
                    Enquiry e = buildEnquiry(
                        col(cols, 0),
                        col(cols, 1), col(cols, 2), col(cols, 3), col(cols, 4),
                        col(cols, 5), col(cols, 6), col(cols, 7), col(cols, 8),
                        col(cols, 9), col(cols, 10), col(cols, 11), col(cols, 12),
                        col(cols, 13), col(cols, 14), col(cols, 15),
                        items
                    );
                    enquiryRepository.save(e);
                    created++;
                } catch (DuplicateRowException ex) {
                    duplicates++;
                } catch (ImportRowException ex) {
                    errors.add("Row " + lineNum + ": " + ex.getMessage());
                    skipped++;
                }
            }
        }
        logger.info("CSV import complete: {} created, {} duplicates, {} skipped", created, duplicates, skipped);
        return new BulkImportResultDTO(created, duplicates, skipped, errors);
    }

    public byte[] generateTemplate() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Enquiry Import");

            List<String> headers = new ArrayList<>(List.of(
                "enqNo(optional)", "opportunityName*", "companyName", "contactPersonName",
                "contactPersonPhone", "contactPersonEmail", "city", "state",
                "enquirySource", "referenceNumber", "expectedRevenue",
                "probability(0-100)", "priority(HOT/WARM/COLD)",
                "status(NEW/CONTACTED/etc)", "enqDate(yyyy-MM-dd)", "nextFollowupDate(yyyy-MM-dd)"
            ));
            for (int i = 1; i <= MAX_ITEMS; i++) {
                headers.add("item" + i);
                headers.add("item" + i + "_qty");
            }

            CellStyle bold = wb.createCellStyle();
            Font f = wb.createFont();
            f.setBold(true);
            bold.setFont(f);

            Row hRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell c = hRow.createCell(i);
                c.setCellValue(headers.get(i));
                c.setCellStyle(bold);
            }

            // Sample data row
            Row sample = sheet.createRow(1);
            String today = LocalDate.now().toString();
            String followup = LocalDate.now().plusDays(7).toString();
            String[] sampleData = {
                "", "Machine Enquiry - ABC Co", "ABC Company Ltd", "Rahul Sharma", "+91 9876543210",
                "rahul@abc.com", "Mumbai", "Maharashtra", "IndiaMart", "REF-001",
                "50000", "60", "WARM", "NEW", today, followup,
                "ITEM-001", "3", "ITEM-002", "", "Custom Gear Box", "2"
            };
            for (int i = 0; i < sampleData.length; i++) sample.createCell(i).setCellValue(sampleData[i]);

            for (int i = 0; i < headers.size(); i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private Enquiry buildEnquiry(
            String enqNo,
            String opportunityName, String companyName, String contactPersonName,
            String contactPersonPhone, String contactPersonEmail,
            String city, String state, String enquirySource, String referenceNumber,
            String expectedRevenue, String probability, String priority,
            String status, String enqDate, String nextFollowupDate,
            List<ItemEntry> items) {

        LocalDate parsedEnqDate = parseDate(enqDate);
        String trimmedOpportunity = trim(opportunityName);

        if (trimmedOpportunity == null && parsedEnqDate == null) {
            throw new ImportRowException("invalid entry: both opportunityName and enqDate are missing");
        }

        // Resolve enquiry number: use provided value or auto-generate
        String resolvedEnqNo = trim(enqNo);
        if (resolvedEnqNo != null) {
            if (enquiryRepository.findByEnqNo(resolvedEnqNo).isPresent()) {
                throw new DuplicateRowException("enqNo already exists: " + resolvedEnqNo);
            }
        } else {
            resolvedEnqNo = enquiryNumberGenerator.next();
        }

        Enquiry e = new Enquiry();
        e.setEnqNo(resolvedEnqNo);
        e.setOpportunityName(trimmedOpportunity);
        e.setContactPersonName(trim(contactPersonName));
        e.setContactPersonPhone(trim(contactPersonPhone));
        e.setContactPersonEmail(trim(contactPersonEmail));
        e.setCity(trim(city));
        e.setState(trim(state));
        e.setEnquirySource(trim(enquirySource));
        e.setReferenceNumber(trim(referenceNumber));

        if (companyName != null && !companyName.isBlank()) {
            List<Contact> matches = contactRepository.searchForDropdown(companyName.trim(), null, PageRequest.of(0, 1));
            if (!matches.isEmpty()) {
                e.setContact(matches.get(0));
            } else {
                e.setManualCompanyName(companyName.trim());
            }
        }

        String resolvedCompany = e.getContact() != null
            ? e.getContact().getCompanyName()
            : (e.getManualCompanyName() != null ? e.getManualCompanyName() : "");

        if (enquiryRepository.existsByDeduplicationKey(trimmedOpportunity, resolvedCompany, parsedEnqDate)) {
            throw new DuplicateRowException("duplicate: " + trimmedOpportunity + " / " + resolvedCompany
                + (parsedEnqDate != null ? " / " + parsedEnqDate : ""));
        }

        e.setExpectedRevenue(parseBigDecimal(expectedRevenue, BigDecimal.ZERO));
        e.setProbability(parseInteger(probability, 0));

        e.setPriority(EnquiryPriority.WARM);
        if (priority != null && !priority.isBlank()) {
            try { e.setPriority(EnquiryPriority.valueOf(priority.trim().toUpperCase())); } catch (IllegalArgumentException ignored) {}
        }

        e.setStatus(EnquiryStatus.NEW);
        if (status != null && !status.isBlank()) {
            try { e.setStatus(EnquiryStatus.valueOf(status.trim().toUpperCase())); } catch (IllegalArgumentException ignored) {}
        }

        e.setEnqDate(parsedEnqDate != null ? parsedEnqDate : LocalDate.now());
        LocalDate parsedFollowup = parseDate(nextFollowupDate);
        e.setNextFollowupDate(parsedFollowup != null ? parsedFollowup : e.getEnqDate().plusDays(7));
        e.setDaysForNextFollowup(7);

        List<EnquiredProducts> products = new ArrayList<>();
        for (ItemEntry item : items) {
            Optional<InventoryItem> matched = inventoryItemRepository.findByItemCodeIgnoreCaseAndDeletedDateIsNull(item.value());
            EnquiredProducts ep = new EnquiredProducts();
            if (matched.isPresent()) {
                ep.setInventoryItem(matched.get());
            } else {
                ep.setProductNameRequired(item.value());
            }
            ep.setQty(item.qty());
            ep.setEnquiry(e);
            products.add(ep);
        }
        e.setEnquiredProducts(products);
        e.setEnquiryConversationRecords(new ArrayList<>());

        return e;
    }

    private LocalDate parseDate(String val) {
        if (val == null || val.isBlank()) return null;
        String v = val.trim();
        DateTimeFormatter[] fmts = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        };
        for (DateTimeFormatter fmt : fmts) {
            try { return LocalDate.parse(v, fmt); } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private BigDecimal parseBigDecimal(String val, BigDecimal defaultVal) {
        if (val == null || val.isBlank()) return defaultVal;
        try { return new BigDecimal(val.trim().replace(",", "")); } catch (NumberFormatException e) { return defaultVal; }
    }

    private Integer parseInteger(String val, Integer defaultVal) {
        if (val == null || val.isBlank()) return defaultVal;
        try { return Integer.parseInt(val.trim()); } catch (NumberFormatException e) { return defaultVal; }
    }

    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                double d = cell.getNumericCellValue();
                yield d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield cell.getStringCellValue(); } catch (Exception ex) { yield String.valueOf(cell.getNumericCellValue()); }
            }
            default -> null;
        };
    }

    private boolean isRowEmpty(Row row) {
        // Check cols 1-5 (opportunityName onwards; col 0 is optional enqNo)
        for (int i = 1; i <= 5; i++) {
            String val = getCellString(row, i);
            if (val != null && !val.isBlank()) return false;
        }
        return true;
    }

    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == '"') { inQuotes = !inQuotes; }
            else if (c == ',' && !inQuotes) { fields.add(sb.toString().trim()); sb = new StringBuilder(); }
            else { sb.append(c); }
        }
        fields.add(sb.toString().trim());
        return fields.toArray(new String[0]);
    }

    private String col(String[] cols, int i) { return i < cols.length ? cols[i] : null; }
    private String trim(String val) { return (val == null || val.isBlank()) ? null : val.trim(); }

    private static class ImportRowException extends RuntimeException {
        ImportRowException(String message) { super(message); }
    }

    private static class DuplicateRowException extends RuntimeException {
        DuplicateRowException(String message) { super(message); }
    }
}
