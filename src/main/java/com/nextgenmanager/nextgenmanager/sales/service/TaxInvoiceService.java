package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.sales.dto.TaxInvoiceCreateDto;
import com.nextgenmanager.nextgenmanager.sales.dto.TaxInvoiceDto;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.List;

public interface TaxInvoiceService {

    TaxInvoiceDto createFromSalesOrder(TaxInvoiceCreateDto dto);

    TaxInvoiceDto getById(Long id);

    Page<TaxInvoiceDto> list(int page, int size);

    List<TaxInvoiceDto> getBySalesOrderId(Long soId);

    TaxInvoiceDto markPaid(Long id, BigDecimal amount);

    TaxInvoiceDto markSent(Long id);

    TaxInvoiceDto cancel(Long id);

    String nextNumber();
}
