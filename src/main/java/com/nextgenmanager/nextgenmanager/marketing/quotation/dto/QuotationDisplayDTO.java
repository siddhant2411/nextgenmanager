package com.nextgenmanager.nextgenmanager.marketing.quotation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuotationDisplayDTO {

    private int id;
    private String qtnNo;
    private LocalDate qtnDate;
    private String enqNo;
    private LocalDate enqDate;
    private String companyName;
    private BigDecimal netAmount;
    private BigDecimal totalAmount;

}
