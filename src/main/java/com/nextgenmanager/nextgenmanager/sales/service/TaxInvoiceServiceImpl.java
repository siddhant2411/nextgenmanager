package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.sales.dto.TaxInvoiceCreateDto;
import com.nextgenmanager.nextgenmanager.sales.dto.TaxInvoiceDto;
import com.nextgenmanager.nextgenmanager.sales.dto.TaxInvoiceItemDto;
import com.nextgenmanager.nextgenmanager.sales.exception.InvalidSalesOrderStateException;
import com.nextgenmanager.nextgenmanager.sales.exception.SalesOrderNotFoundException;
import com.nextgenmanager.nextgenmanager.sales.model.*;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesOrderRepository;
import com.nextgenmanager.nextgenmanager.sales.repository.TaxInvoiceRepository;
import com.nextgenmanager.nextgenmanager.sales.repository.DeliveryNoteRepository;
import com.nextgenmanager.nextgenmanager.company.repository.CompanyDetailsRepository;
import com.nextgenmanager.nextgenmanager.company.model.CompanyDetails;
import com.nextgenmanager.nextgenmanager.common.events.DomainEventPublisher;
import com.nextgenmanager.nextgenmanager.common.events.DocumentVoidedEvent;
import com.nextgenmanager.nextgenmanager.common.events.SourceDocTypes;
import com.nextgenmanager.nextgenmanager.sales.events.TaxInvoiceIssuedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class TaxInvoiceServiceImpl implements TaxInvoiceService {

    private static final Logger logger = LoggerFactory.getLogger(TaxInvoiceServiceImpl.class);

    private final TaxInvoiceRepository taxInvoiceRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final TaxInvoiceNumberGenerator numberGenerator;
    private final CompanyDetailsRepository companyDetailsRepository;
    private final DeliveryNoteRepository deliveryNoteRepository;
    private final DomainEventPublisher domainEventPublisher;

    @Override
    public TaxInvoiceDto createFromSalesOrder(TaxInvoiceCreateDto dto) {
        if (dto.getSalesOrderId() == null) {
            throw new IllegalArgumentException("salesOrderId is required");
        }
        SalesOrder so = salesOrderRepository.findById(dto.getSalesOrderId())
                .orElseThrow(() -> new SalesOrderNotFoundException(dto.getSalesOrderId()));

        if (so.getStatus() != SalesOrderStatus.FULLY_DISPATCHED && so.getStatus() != SalesOrderStatus.APPROVED) {
            throw new InvalidSalesOrderStateException(
                    "Cannot create invoice for SO " + so.getOrderNumber()
                            + " in status " + so.getStatus()
                            + ". SO must be APPROVED or FULLY_DISPATCHED.");
        }

        TaxInvoice invoice = new TaxInvoice();
        invoice.setInvoiceNumber(dto.getInvoiceNumber() != null && !dto.getInvoiceNumber().isBlank()
                ? dto.getInvoiceNumber() : numberGenerator.next());
        invoice.setSalesOrder(so);
        invoice.setInvoiceDate(dto.getInvoiceDate() != null ? dto.getInvoiceDate() : LocalDate.now());
        invoice.setDueDate(dto.getDueDate());
        invoice.setStatus(TaxInvoiceStatus.DRAFT);
        invoice.setPaidAmount(dto.getPaidAmount() != null ? dto.getPaidAmount() : BigDecimal.ZERO);

        // Copy financials from SO at the time of invoicing
        invoice.setSubTotal(so.getSubTotal());
        invoice.setTaxableValue(so.getTaxableValue());
        invoice.setCgstAmount(so.getCgstAmount());
        invoice.setSgstAmount(so.getSgstAmount());
        invoice.setIgstAmount(so.getIgstAmount());
        invoice.setTotalPayableAmount(so.getTotalPayableAmount());

        // Copy line items from SO
        for (SalesOrderItem soItem : so.getItems()) {
            InvoiceItem item = new InvoiceItem();
            item.setTaxInvoice(invoice);
            item.setInventoryItem(soItem.getInventoryItem());
            item.setQty(soItem.getQty());
            item.setPricePerUnit(soItem.getPricePerUnit());
            item.setCgstAmount(soItem.getCgstAmount());
            item.setSgstAmount(soItem.getSgstAmount());
            item.setIgstAmount(soItem.getIgstAmount());
            
            // Total for invoice line should be taxable + tax
            BigDecimal lineNet = soItem.getTotalAmountOfProduct() != null ? soItem.getTotalAmountOfProduct() : BigDecimal.ZERO;
            BigDecimal lineTax = (soItem.getCgstAmount() != null ? soItem.getCgstAmount() : BigDecimal.ZERO)
                    .add(soItem.getSgstAmount() != null ? soItem.getSgstAmount() : BigDecimal.ZERO)
                    .add(soItem.getIgstAmount() != null ? soItem.getIgstAmount() : BigDecimal.ZERO);
            item.setTotalAmount(lineNet.add(lineTax));


            // Extract serial numbers if any
            if (soItem.getInventoryInstanceList() != null && !soItem.getInventoryInstanceList().isEmpty()) {
                List<String> sns = soItem.getInventoryInstanceList().stream()
                        .map(ii -> ii.getSerialNumber() != null ? ii.getSerialNumber().getSerialNumber() : null)
                        .filter(java.util.Objects::nonNull)
                        .toList();
                if (!sns.isEmpty()) {
                    item.setSerialNumbers(String.join(", ", sns));
                }
            }

            invoice.getItems().add(item);
        }

        // Advance SO status to INVOICED
        so.setStatus(SalesOrderStatus.INVOICED);
        salesOrderRepository.save(so);

        TaxInvoice saved = taxInvoiceRepository.save(invoice);
        logger.info("TaxInvoice {} created for SO {}", saved.getInvoiceNumber(), so.getOrderNumber());

        // Accounting auto-posts the SALES voucher (listener runs after this tx commits).
        domainEventPublisher.publish(new TaxInvoiceIssuedEvent(saved.getId()));
        return toDto(saved);
    }

    @Override
    public TaxInvoiceDto getById(Long id) {
        TaxInvoice invoice = taxInvoiceRepository.findByIdAndDeletedDateIsNull(id)
                .orElseThrow(() -> new SalesOrderNotFoundException("Invoice not found: " + id));
        return toDto(invoice);
    }

    @Override
    public Page<TaxInvoiceDto> list(int page, int size) {
        return taxInvoiceRepository.findByDeletedDateIsNull(
                PageRequest.of(page, size, Sort.by("creationDate").descending())
        ).map(this::toDto);
    }

    @Override
    public List<TaxInvoiceDto> getBySalesOrderId(Long soId) {
        return taxInvoiceRepository.findBySalesOrderIdAndDeletedDateIsNull(soId)
                .stream().map(this::toDto).toList();
    }

    @Override
    public TaxInvoiceDto markPaid(Long id, BigDecimal amount) {
        TaxInvoice invoice = fetchById(id);
        assertNotCancelled(invoice);
        
        BigDecimal currentPaid = invoice.getPaidAmount() != null ? invoice.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal total = invoice.getTotalPayableAmount() != null ? invoice.getTotalPayableAmount() : BigDecimal.ZERO;
        
        if (amount == null) {
            // Mark as fully paid
            invoice.setPaidAmount(total);
        } else {
            // Add incremental payment
            invoice.setPaidAmount(currentPaid.add(amount));
        }

        if (invoice.getPaidAmount().compareTo(total) >= 0) {
            invoice.setStatus(TaxInvoiceStatus.PAID);
        }

        logger.info("TaxInvoice {} marked paid: {}", invoice.getInvoiceNumber(), invoice.getPaidAmount());
        return toDto(taxInvoiceRepository.save(invoice));
    }

    @Override
    public TaxInvoiceDto markSent(Long id) {
        TaxInvoice invoice = fetchById(id);
        assertNotCancelled(invoice);
        invoice.setStatus(TaxInvoiceStatus.SENT);
        logger.info("TaxInvoice {} marked as sent", invoice.getInvoiceNumber());
        return toDto(taxInvoiceRepository.save(invoice));
    }

    @Override
    public TaxInvoiceDto cancel(Long id) {
        TaxInvoice invoice = fetchById(id);
        if (invoice.getStatus() == TaxInvoiceStatus.PAID) {
            throw new InvalidSalesOrderStateException(
                    "Cannot cancel a PAID invoice: " + invoice.getInvoiceNumber());
        }
        invoice.setStatus(TaxInvoiceStatus.CANCELLED);
        invoice.setDeletedDate(new Date());
        logger.info("TaxInvoice {} cancelled", invoice.getInvoiceNumber());
        TaxInvoice saved = taxInvoiceRepository.save(invoice);
        // Accounting reverses the SALES voucher (listener runs after this tx commits).
        domainEventPublisher.publish(new DocumentVoidedEvent(SourceDocTypes.TAX_INVOICE, id, "Invoice cancelled"));
        return toDto(saved);
    }

    @Override
    public String nextNumber() {
        return numberGenerator.preview();
    }

    // ---- helpers ----

    private TaxInvoice fetchById(Long id) {
        return taxInvoiceRepository.findByIdAndDeletedDateIsNull(id)
                .orElseThrow(() -> new SalesOrderNotFoundException("Invoice not found: " + id));
    }

    private void assertNotCancelled(TaxInvoice invoice) {
        if (invoice.getStatus() == TaxInvoiceStatus.CANCELLED) {
            throw new InvalidSalesOrderStateException(
                    "Invoice " + invoice.getInvoiceNumber() + " is CANCELLED.");
        }
    }

    private TaxInvoiceDto toDto(TaxInvoice inv) {
        TaxInvoiceDto dto = new TaxInvoiceDto();
        dto.setId(inv.getId());
        dto.setInvoiceNumber(inv.getInvoiceNumber());
        dto.setInvoiceDate(inv.getInvoiceDate());
        dto.setDueDate(inv.getDueDate());
        dto.setStatus(inv.getStatus());
        dto.setSubTotal(inv.getSubTotal());
        dto.setTaxableValue(inv.getTaxableValue());
        dto.setCgstAmount(inv.getCgstAmount());
        dto.setSgstAmount(inv.getSgstAmount());
        dto.setIgstAmount(inv.getIgstAmount());
        dto.setTotalPayableAmount(inv.getTotalPayableAmount());
        dto.setPaidAmount(inv.getPaidAmount());
        dto.setCreationDate(inv.getCreationDate());
        dto.setUpdatedDate(inv.getUpdatedDate());

        if (inv.getSalesOrder() != null) {
            dto.setSalesOrderId(inv.getSalesOrder().getId());
            dto.setSalesOrderNumber(inv.getSalesOrder().getOrderNumber());
            dto.setDeliveryAddress(inv.getSalesOrder().getDeliveryAddress());
            dto.setDiscountAmount(inv.getSalesOrder().getDiscountAmount());
            if (inv.getSalesOrder().getCustomer() != null) {
                dto.setCustomerName(inv.getSalesOrder().getCustomer().getCompanyName());
                dto.setCustomerGstin(inv.getSalesOrder().getCustomer().getGstNumber());
                if (inv.getSalesOrder().getCustomer().getAddresses() != null && !inv.getSalesOrder().getCustomer().getAddresses().isEmpty()) {
                    com.nextgenmanager.nextgenmanager.contact.model.ContactAddress addr = inv.getSalesOrder().getCustomer().getAddresses().get(0);
                    String addressStr = addr.getStreet1() != null ? addr.getStreet1() : "";
                    if (addr.getStreet2() != null) addressStr += ", " + addr.getStreet2();
                    if (addr.getCity() != null) addressStr += ", " + addr.getCity();
                    if (addr.getState() != null) addressStr += ", " + addr.getState();
                    if (addr.getPinCode() != null) addressStr += " - " + addr.getPinCode();
                    dto.setCustomerAddress(addressStr);
                }
            }
            
            // Fetch related Delivery Notes (Challans)
            List<DeliveryNote> dns = deliveryNoteRepository.findBySalesOrderId(inv.getSalesOrder().getId(), PageRequest.of(0, 100)).getContent();
            dto.setDeliveryNoteNumbers(dns.stream().map(DeliveryNote::getDeliveryNoteNo).toList());
        }

        // Populating host company details
        companyDetailsRepository.findAll().stream().findFirst().ifPresent(cd -> {
            dto.setCompanyName(cd.getCompanyName());
            String addr = (cd.getStreet1() != null ? cd.getStreet1() : "") +
                         (cd.getStreet2() != null ? ", " + cd.getStreet2() : "") +
                         (cd.getCity() != null ? ", " + cd.getCity() : "") +
                         (cd.getState() != null ? ", " + cd.getState() : "") +
                         (cd.getPinCode() != null ? " - " + cd.getPinCode() : "");
            dto.setCompanyAddress(addr);
            dto.setCompanyGstin(cd.getGstNumber());
            dto.setCompanyPan(cd.getPanNumber());
        });

        if (inv.getItems() != null) {
            dto.setItems(inv.getItems().stream().map(this::toItemDto).toList());
        }
        return dto;
    }

    private TaxInvoiceItemDto toItemDto(InvoiceItem item) {
        TaxInvoiceItemDto dto = new TaxInvoiceItemDto();
        dto.setId(item.getId());
        dto.setQty(item.getQty());
        dto.setPricePerUnit(item.getPricePerUnit());
        dto.setCgstAmount(item.getCgstAmount());
        dto.setSgstAmount(item.getSgstAmount());
        dto.setIgstAmount(item.getIgstAmount());
        dto.setTotalAmount(item.getTotalAmount());
        if (item.getInventoryItem() != null) {
            dto.setInventoryItemId(item.getInventoryItem().getInventoryItemId());
            dto.setInventoryItemName(item.getInventoryItem().getName());
            dto.setDescription(item.getInventoryItem().getRemarks());
            dto.setHsnCode(item.getInventoryItem().getHsnCode());
            dto.setSerialNumbers(item.getSerialNumbers());
        }
        return dto;
    }
}
