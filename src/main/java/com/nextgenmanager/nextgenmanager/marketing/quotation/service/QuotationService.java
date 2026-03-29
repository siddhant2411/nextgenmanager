package com.nextgenmanager.nextgenmanager.marketing.quotation.service;

import com.nextgenmanager.nextgenmanager.marketing.quotation.dto.QuotationDisplayDTO;
import com.nextgenmanager.nextgenmanager.marketing.quotation.model.Quotation;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface QuotationService {

    public Quotation getQuotationById(Long id);

    public List<Quotation>  getQuotationList();

    public Quotation createQuotation(Quotation quotation) throws Exception;

    public Quotation updateQuotation(Quotation updatedQuotation, Long id) throws Exception;

    public void deleteQuotation(Long id);

    public Page<QuotationDisplayDTO> getQuotationDisplayList(int page, int size, String sortBy, String sortDir,
                                                             String qtnNoFilter, LocalDate qtnDateFilter, LocalDate enqDateFilter,
                                                             String enqNoFilter, String companyNameFilter, BigDecimal netAmountFilter,
                                                             BigDecimal totalAmountFilter);

    public byte[] generateQuotationPdf(String html);

    public ResponseEntity<byte[]> downloadQuotationPdf(Long qtnId);

    public List<Quotation> getQuotationsByEnquiryId(Long enquiryId);

}
