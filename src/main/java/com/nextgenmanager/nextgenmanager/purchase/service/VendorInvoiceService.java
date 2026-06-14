package com.nextgenmanager.nextgenmanager.purchase.service;

import com.nextgenmanager.nextgenmanager.common.model.FileAttachment;
import com.nextgenmanager.nextgenmanager.purchase.dto.CreateVendorInvoiceRequest;
import com.nextgenmanager.nextgenmanager.purchase.dto.VendorInvoiceDto;
import com.nextgenmanager.nextgenmanager.purchase.dto.VendorInvoiceListDto;
import io.minio.GetObjectResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface VendorInvoiceService {

    VendorInvoiceDto create(CreateVendorInvoiceRequest request);

    VendorInvoiceDto getById(Long id);

    List<VendorInvoiceListDto> getByPo(Long poId);

    VendorInvoiceDto post(Long id);

    VendorInvoiceDto cancel(Long id);

    List<FileAttachment> getAttachments(Long invoiceId);

    FileAttachment getAttachment(Long fileId);

    void uploadAttachment(Long invoiceId, MultipartFile file) throws Exception;

    void deleteAttachment(Long fileId) throws Exception;

    GetObjectResponse downloadAttachment(Long fileId) throws Exception;
}
