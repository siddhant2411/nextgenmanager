package com.nextgenmanager.nextgenmanager.purchase.service;

import com.nextgenmanager.nextgenmanager.purchase.dto.CreateVendorInvoiceRequest;
import com.nextgenmanager.nextgenmanager.purchase.dto.VendorInvoiceDto;
import com.nextgenmanager.nextgenmanager.purchase.dto.VendorInvoiceListDto;

import java.util.List;

public interface VendorInvoiceService {

    VendorInvoiceDto create(CreateVendorInvoiceRequest request);

    VendorInvoiceDto getById(Long id);

    List<VendorInvoiceListDto> getByPo(Long poId);

    VendorInvoiceDto post(Long id);

    VendorInvoiceDto cancel(Long id);
}
