package com.nextgenmanager.nextgenmanager.marketing.enquiry.service;

import com.nextgenmanager.nextgenmanager.contact.repository.ContactRepository;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.BulkImportResultDTO;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.Enquiry;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryCloseOutcome;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryCloseReason;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryConversationRecord;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryStatus;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.repository.EnquiryCloseReasonRepository;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.repository.EnquiryRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the import column layout and the follow-up log it now carries.
 *
 * The layout is the fragile part: adding description and conversationLog pushed the item pairs
 * from column 19 to column 21, and an off-by-two there loads item codes into a note field without
 * failing anything.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EnquiryImportServiceTest {

    @Mock private EnquiryRepository enquiryRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private EnquiryNumberGenerator enquiryNumberGenerator;
    @Mock private EnquiryCloseReasonRepository closeReasonRepository;

    @InjectMocks private EnquiryImportService service;

    /** Header plus one data row, in the exact order the generated template declares. */
    private static MockMultipartFile workbookWith(String[] dataRow) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Enquiry Import");
            Row header = sheet.createRow(0);
            for (int i = 0; i < dataRow.length; i++) header.createCell(i).setCellValue("col" + i);
            Row row = sheet.createRow(1);
            for (int i = 0; i < dataRow.length; i++) row.createCell(i).setCellValue(dataRow[i]);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return new MockMultipartFile("file", "enquiries.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    private void stubHappyPath() {
        when(enquiryNumberGenerator.next()).thenReturn("ENQ-0001");
        when(contactRepository.searchForDropdown(anyString(), any(), any())).thenReturn(Collections.emptyList());
        when(enquiryRepository.existsByDeduplicationKey(any(), any(), any())).thenReturn(false);
        when(inventoryItemRepository.findByItemCodeIgnoreCaseAndDeletedDateIsNull(anyString()))
                .thenReturn(Optional.empty());
        when(enquiryRepository.save(any(Enquiry.class))).thenAnswer(i -> i.getArgument(0));

        EnquiryCloseReason noResponse = new EnquiryCloseReason();
        noResponse.setId(4L);
        noResponse.setCode("NO_RESPONSE");
        noResponse.setOutcome(EnquiryCloseOutcome.NO_ENGAGEMENT);
        when(closeReasonRepository.findByCodeIgnoreCase("NO_RESPONSE")).thenReturn(Optional.of(noResponse));
    }

    private Enquiry importSingleRow(String[] dataRow) throws Exception {
        stubHappyPath();
        BulkImportResultDTO result = service.importFromFile(workbookWith(dataRow));
        assertThat(result.getCreated()).isEqualTo(1);

        ArgumentCaptor<Enquiry> saved = ArgumentCaptor.forClass(Enquiry.class);
        verify(enquiryRepository).save(saved.capture());
        return saved.getValue();
    }

    /** A row carrying every column the template declares, including two item pairs. */
    private static String[] fullRow(String conversationLog) {
        return new String[]{
                "",                                   // 0  enqNo
                "Strainer enquiry - Acme",            // 1  opportunityName
                "Acme Engineering Pvt Ltd",           // 2  companyName
                "R Mehta",                            // 3  contactPersonName
                "+91 9876543210",                     // 4  contactPersonPhone
                "r.mehta@acme.test",                  // 5  contactPersonEmail
                "Rajkot",                             // 6  city
                "Gujarat",                            // 7  state
                "IndiaMart",                          // 8  enquirySource
                "REF-77",                             // 9  referenceNumber
                "250000",                             // 10 expectedRevenue
                "40",                                 // 11 probability
                "HOT",                                // 12 priority
                "CLOSED",                             // 13 status
                "2026-03-01",                         // 14 enqDate
                "",                                   // 15 nextFollowupDate
                "NO_RESPONSE",                        // 16 closeReasonCode
                "Chased four times, never replied",   // 17 closeReasonText
                "2026-06-20",                         // 18 closedDate
                "Customer asked for a revised GA drawing.", // 19 description
                conversationLog,                      // 20 conversationLog
                "STR-2112", "3",                      // 21/22 item1
                "Custom flange", "1"                  // 23/24 item2
        };
    }

    @Test
    void itemPairsAreReadFromColumn21NotColumn19() throws Exception {
        Enquiry saved = importSingleRow(fullRow(""));

        assertThat(saved.getEnquiredProducts()).hasSize(2);
        assertThat(saved.getEnquiredProducts())
                .extracting(p -> p.getProductNameRequired())
                .containsExactly("STR-2112", "Custom flange");
        // The description column must not have been swept up as an item.
        assertThat(saved.getDescription()).isEqualTo("Customer asked for a revised GA drawing.");
    }

    @Test
    void followUpLogBecomesDatedConversationRecords() throws Exception {
        Enquiry saved = importSingleRow(fullRow(
                "2026-03-04|EMAIL|Mail done ;; 2026-03-18|CALL|Called, no answer ;; 2026-04-02|EMAIL|Reminder sent"));

        assertThat(saved.getEnquiryConversationRecords()).hasSize(3);
        assertThat(saved.getEnquiryConversationRecords())
                .extracting(EnquiryConversationRecord::getConversationDate)
                .containsExactly(LocalDate.of(2026, 3, 4), LocalDate.of(2026, 3, 18), LocalDate.of(2026, 4, 2));
        assertThat(saved.getEnquiryConversationRecords())
                .extracting(EnquiryConversationRecord::getConversationType)
                .containsExactly(EnquiryConversationRecord.ConversationType.EMAIL,
                        EnquiryConversationRecord.ConversationType.CALL,
                        EnquiryConversationRecord.ConversationType.EMAIL);
        assertThat(saved.getEnquiryConversationRecords().get(1).getConversation())
                .isEqualTo("Called, no answer");
        // Every record is attached, or the cascade saves nothing.
        assertThat(saved.getEnquiryConversationRecords())
                .allSatisfy(r -> assertThat(r.getEnquiry()).isSameAs(saved));
    }

    @Test
    void lastContactedDateIsTheNewestEntryInTheLog() throws Exception {
        Enquiry saved = importSingleRow(fullRow(
                "2026-04-02|EMAIL|Reminder sent ;; 2026-03-04|EMAIL|Mail done"));

        // Out of order on purpose: the column has to be the maximum, not the last row read.
        assertThat(saved.getLastContactedDate()).isEqualTo(LocalDate.of(2026, 4, 2));
    }

    @Test
    void anEnquiryWithNoLogKeepsANullLastContactedDate() throws Exception {
        Enquiry saved = importSingleRow(fullRow(""));

        assertThat(saved.getEnquiryConversationRecords()).isEmpty();
        assertThat(saved.getLastContactedDate()).isNull();
    }

    @Test
    void entriesWithoutADateOrATypeStillLoad() throws Exception {
        Enquiry saved = importSingleRow(fullRow("Spoke at the trade show ;; CALL|Asked for budget"));

        List<EnquiryConversationRecord> records = saved.getEnquiryConversationRecords();
        assertThat(records).hasSize(2);
        assertThat(records.get(0).getConversation()).isEqualTo("Spoke at the trade show");
        assertThat(records.get(0).getConversationDate()).isNull();
        assertThat(records.get(0).getConversationType())
                .isEqualTo(EnquiryConversationRecord.ConversationType.NOTE);
        assertThat(records.get(1).getConversationType())
                .isEqualTo(EnquiryConversationRecord.ConversationType.CALL);
        assertThat(records.get(1).getConversation()).isEqualTo("Asked for budget");
    }

    @Test
    void closedEnquiriesGetNoFollowUpDateEvenWhenTheyHaveALog() throws Exception {
        Enquiry saved = importSingleRow(fullRow("2026-03-04|EMAIL|Mail done"));

        assertThat(saved.getStatus()).isEqualTo(EnquiryStatus.CLOSED);
        // Importing a year of history must not stack up hundreds of overdue reminders dated in
        // the past; only live enquiries get the seven-day default.
        assertThat(saved.getNextFollowupDate()).isNull();
        assertThat(saved.getDaysForNextFollowup()).isZero();
        assertThat(saved.getCloseReasonCode()).isNotNull();
        assertThat(saved.getCloseReasonCode().getCode()).isEqualTo("NO_RESPONSE");
    }

    @Test
    void generatedTemplateRoundTripsThroughTheImporter() throws Exception {
        stubHappyPath();

        // The template's own sample row has to survive its own importer -- that is the contract a
        // user relies on when they download it, fill it in and upload it back.
        byte[] template = service.generateTemplate();
        MockMultipartFile file = new MockMultipartFile("file", "Enquiry_Import_Template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", template);

        BulkImportResultDTO result = service.importFromFile(file);

        assertThat(result.getCreated()).isEqualTo(1);
        assertThat(result.getErrors()).isEmpty();

        ArgumentCaptor<Enquiry> saved = ArgumentCaptor.forClass(Enquiry.class);
        verify(enquiryRepository).save(saved.capture());
        assertThat(saved.getValue().getEnquiredProducts())
                .extracting(p -> p.getProductNameRequired())
                .containsExactly("ITEM-001", "ITEM-002", "Custom Gear Box");
        assertThat(saved.getValue().getEnquiryConversationRecords()).hasSize(2);
    }
}
