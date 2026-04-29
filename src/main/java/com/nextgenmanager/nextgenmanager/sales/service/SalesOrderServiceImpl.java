package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequest;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequestSource;
import com.nextgenmanager.nextgenmanager.Inventory.service.InventoryInstanceService;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.contact.repository.ContactRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.marketing.quotation.model.Quotation;
import com.nextgenmanager.nextgenmanager.marketing.quotation.repository.QuotationRepository;
import com.nextgenmanager.nextgenmanager.sales.dto.SalesOrderCreateDto;
import com.nextgenmanager.nextgenmanager.sales.dto.SalesOrderDto;
import com.nextgenmanager.nextgenmanager.sales.dto.SalesOrderItemDto;
import com.nextgenmanager.nextgenmanager.sales.mapper.SalesOrderMapper;
import com.nextgenmanager.nextgenmanager.sales.model.*;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesOrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SalesOrderServiceImpl implements SalesOrderService {

    private static final Logger logger = LoggerFactory.getLogger(SalesOrderServiceImpl.class);

    @Autowired
    private final SalesOrderRepository salesOrderRepository;

    @Autowired
    private final ContactRepository contactRepository;

    @Autowired
    private final QuotationRepository quotationRepository;

    @Autowired
    private final InventoryItemRepository inventoryItemRepository;

    @Autowired
    private final InventoryInstanceService inventoryInstanceService;

    @Autowired
    private final SalesOrderMapper salesOrderMapper;

    @Override
    public SalesOrderDto createSalesOrder(SalesOrderCreateDto dto) {
        logger.info("Creating Sales Order for customerId={}, quotationId={}",
                dto.getCustomerId(), dto.getQuotationId());

        // 1. Resolve customer
        Contact customer = contactRepository.findById(dto.getCustomerId())
                .orElseThrow(() -> {
                    logger.error("Customer not found with id={}", dto.getCustomerId());
                    return new ResourceNotFoundException("Customer not found");
                });

        // 2. Optional quotation — must be ACCEPTED before a Sales Order can reference it
        Quotation quotation = null;
        if (dto.getQuotationId() > 0) {
            quotation = quotationRepository.findById(dto.getQuotationId())
                    .orElseThrow(() -> {
                        logger.error("Quotation not found with id={}", dto.getQuotationId());
                        return new ResourceNotFoundException("Quotation not found");
                    });
            if (quotation.getQuotationStatus() != com.nextgenmanager.nextgenmanager.marketing.quotation.model.QuotationStatus.ACCEPTED) {
                throw new IllegalStateException(
                        "Quotation '" + quotation.getQtnNo() + "' must be in ACCEPTED status before creating a Sales Order. Current status: " + quotation.getQuotationStatus());
            }
        }

        // 3. Create SalesOrder entity
        SalesOrder so = new SalesOrder();
        so.setCustomer(customer);
        so.setOrderDate(dto.getOrderDate());
        so.setQuotation(quotation);
        so.setCurrency(dto.getCurrency());
        so.setPaymentTerms(dto.getPaymentTerms());
        so.setIncoterms(dto.getIncoterms());
        so.setVoucherType(VoucherType.SALES_ORDER);
        so.setPoNumber(dto.getPoNumber());
        so.setPoDate(dto.getPoDate());
        so.setDiscountPercentage(so.getDiscountPercentage());


        so.setStatus(SalesOrderStatus.DRAFT);

        // generate order number
        so.setOrderNumber(generateOrderNumber());
        logger.debug("Generated order number: {}", so.getOrderNumber());

        BigDecimal subTotal = BigDecimal.ZERO;
        // 4. Map items if provided
        if (dto.getItems() != null && !dto.getItems().isEmpty()) {
            List<SalesOrderItem> itemEntities = new ArrayList<>();
            for (SalesOrderItemDto itemDto : dto.getItems()) {
                InventoryItem invItem = inventoryItemRepository.findById(itemDto.getInventoryItem().getInventoryItemId())
                        .orElseThrow(() -> {
                            logger.error("Inventory item not found with id={}", itemDto.getInventoryItem().getInventoryItemId());
                            return new ResourceNotFoundException("Inventory Item not found");
                        });

                SalesOrderItem item = new SalesOrderItem();
                item.setInventoryItem(invItem);
                item.setQty(itemDto.getQty());
                item.setPricePerUnit(itemDto.getPricePerUnit());
                item.setDiscountPercentage(itemDto.getDiscountPercentage());

                // price after discount per unit
                BigDecimal discountedUnitPrice = itemDto.getPricePerUnit()
                        .subtract(itemDto.getPricePerUnit()
                                .multiply(itemDto.getDiscountPercentage().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)));

                item.setUnitPriceAfterDiscount(discountedUnitPrice);

                BigDecimal lineTotal = discountedUnitPrice.multiply(itemDto.getQty());
                item.setTotalAmountOfProduct(lineTotal);

                item.setHsnCode(invItem.getHsnCode());
                item.setSalesOrder(so);
                itemEntities.add(item);

                subTotal = subTotal.add(lineTotal);
                logger.debug("Mapped item: invId={}, qty={}, rate={}",
                        itemDto.getInventoryItem().getInventoryItemId(), itemDto.getQty(), itemDto.getPricePerUnit());
            }
            so.setItems(itemEntities);
        }

        so.setSubTotal(subTotal);
        BigDecimal discountPercent = dto.getDiscountPercentage() != null ? dto.getDiscountPercentage() : BigDecimal.ZERO;
        so.setDiscountPercentage(discountPercent);

        BigDecimal discountAmount = subTotal.multiply(discountPercent.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        so.setDiscountAmount(discountAmount);

        so.setIncludeFreightCharges(dto.isIncludeFreightCharges());
        
        BigDecimal freightCharges = dto.getFreightAndForwardingCharges() != null ? dto.getFreightAndForwardingCharges() : BigDecimal.ZERO;
        so.setFreightAndForwardingCharges(freightCharges);

        BigDecimal taxableValue = subTotal.subtract(discountAmount);
        if (dto.isIncludeFreightCharges()) {
            taxableValue = taxableValue.add(freightCharges);
        }
        so.setTaxableValue(taxableValue);

        BigDecimal taxPercentage = dto.getTaxPercentage() != null ? dto.getTaxPercentage() : BigDecimal.ZERO;
        BigDecimal taxValue = so.getTaxableValue().multiply(taxPercentage).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        if(dto.getTaxType()==TaxType.CGST_SGST){

            so.setTaxType(TaxType.CGST_SGST);
            so.setSgstAmount(taxValue.divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP));
            so.setCgstAmount(taxValue.divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP));

        }
        else if(dto.getTaxType()==TaxType.IGST){

            so.setTaxType(TaxType.IGST);
            so.setIgstAmount(taxValue);
        }

        so.setNetAmount(so.getTaxableValue().add(taxValue));

        if(!dto.isIncludeFreightCharges()){
         so.setNetAmount(so.getNetAmount().add(dto.getFreightAndForwardingCharges()));
        }

        BigDecimal rounded = BigDecimal.valueOf(Math.round(so.getNetAmount().doubleValue()));
        BigDecimal roundOff = so.getNetAmount().subtract(rounded);
        so.setRoundOffAmount(roundOff);
        so.setTotalPayableAmount(rounded);


        so.setDeliveryAddress(dto.getDeliveryAddress());
        so.setDispatchThrough(dto.getDispatchThrough());
        so.setTransportMode(dto.getTransportMode());
        so.setDeliveryDate(dto.getDeliveryDate());
        so.setPackagingInstructions(dto.getPackagingInstructions());
        so.setRemarks(dto.getRemarks());
        so.setReference(dto.getReference());
        try {
            SalesOrder saved = salesOrderRepository.save(so);
            logger.info("Sales Order created successfully with id={}, orderNumber={}", saved.getId(), saved.getOrderNumber());
            return salesOrderMapper.toDTO(saved);
        } catch (Exception e) {
            logger.error("Error occurred while saving Sales Order: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create Sales Order, please try again later.");
        }
    }



    // TODO: implement a proper unique sequence generator
    private String generateOrderNumber() {
        // Example: SO/2025/0001 (improve as needed)
        long count = salesOrderRepository.count() + 1;
        return String.format("SO/%d/%04d", LocalDate.now().getYear(), count);
    }

    public List<SalesOrder> getAllSalesOrders() {
        return salesOrderRepository.findAll();
    }

    @Override
    public SalesOrderDto getSalesOrderById(Long id) {
        logger.info("Fetching Sales Order with id={}", id);

        SalesOrder salesOrder = salesOrderRepository.findById(id)
                .orElseThrow(() -> {
                    logger.error("Sales Order not found with id={}", id);
                    return new ResourceNotFoundException("Sales Order not found with id " + id);
                });

        return salesOrderMapper.toDTO(salesOrder);
    }

    @Override
    public SalesOrderDto updateSalesOrder(Long id, SalesOrderCreateDto dto) {
        logger.info("Updating Sales Order with id={}", id);

        SalesOrder existing = salesOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sales Order not found with id " + id));

        // 1. Customer
        if (dto.getCustomerId() > 0) {
            Contact customer = contactRepository.findById(dto.getCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
            existing.setCustomer(customer);
        }

        // 2. Quotation
        if (dto.getQuotationId() > 0) {
            Quotation quotation = quotationRepository.findById(dto.getQuotationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Quotation not found"));
            existing.setQuotation(quotation);
        } else {
            existing.setQuotation(null);
        }

        // 3. Basic fields
        existing.setOrderDate(dto.getOrderDate());
        existing.setCurrency(dto.getCurrency());
        existing.setPaymentTerms(dto.getPaymentTerms());
        existing.setIncoterms(dto.getIncoterms());
        existing.setPoNumber(dto.getPoNumber());
        existing.setPoDate(dto.getPoDate());

        // 4. Replace items + recalc subtotal
        BigDecimal subTotal = BigDecimal.ZERO;
        existing.getItems().clear();

        if (dto.getItems() != null && !dto.getItems().isEmpty()) {
            for (SalesOrderItemDto itemDto : dto.getItems()) {
                InventoryItem invItem = inventoryItemRepository.findById(itemDto.getInventoryItem().getInventoryItemId())
                        .orElseThrow(() -> new ResourceNotFoundException("Inventory Item not found"));

                SalesOrderItem item = new SalesOrderItem();
                item.setInventoryItem(invItem);
                item.setQty(itemDto.getQty());
                item.setPricePerUnit(itemDto.getPricePerUnit());
                item.setDiscountPercentage(itemDto.getDiscountPercentage());

                // recalc unit price after discount
                BigDecimal discountedUnitPrice = itemDto.getPricePerUnit()
                        .subtract(itemDto.getPricePerUnit()
                                .multiply(itemDto.getDiscountPercentage().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)));
                item.setUnitPriceAfterDiscount(discountedUnitPrice);

                // recalc line total
                BigDecimal lineTotal = discountedUnitPrice.multiply(itemDto.getQty());
                item.setTotalAmountOfProduct(lineTotal);

                item.setHsnCode(invItem.getHsnCode());
                item.setSalesOrder(existing);

                existing.getItems().add(item);
                subTotal = subTotal.add(lineTotal);
            }
        }

        // 5. Discounts + Freight
        existing.setSubTotal(subTotal);
        
        BigDecimal discountPercent = dto.getDiscountPercentage() != null ? dto.getDiscountPercentage() : BigDecimal.ZERO;
        existing.setDiscountPercentage(discountPercent);
        
        BigDecimal discountAmount = subTotal.multiply(discountPercent.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        existing.setDiscountAmount(discountAmount);

        existing.setIncludeFreightCharges(dto.isIncludeFreightCharges());
        
        BigDecimal freightCharges = dto.getFreightAndForwardingCharges() != null ? dto.getFreightAndForwardingCharges() : BigDecimal.ZERO;
        existing.setFreightAndForwardingCharges(freightCharges);

        BigDecimal taxableValue = subTotal.subtract(discountAmount);
        if (dto.isIncludeFreightCharges()) {
            taxableValue = taxableValue.add(freightCharges);
        }
        existing.setTaxableValue(taxableValue);

        // 6. Tax
        BigDecimal taxPercentage = dto.getTaxPercentage() != null ? dto.getTaxPercentage() : BigDecimal.ZERO;
        BigDecimal taxValue = taxableValue.multiply(taxPercentage).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);

        if (dto.getTaxType() == TaxType.CGST_SGST) {
            existing.setTaxType(TaxType.CGST_SGST);
            existing.setSgstAmount(taxValue.divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP));
            existing.setCgstAmount(taxValue.divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP));
            existing.setIgstAmount(BigDecimal.ZERO);
        } else if (dto.getTaxType() == TaxType.IGST) {
            existing.setTaxType(TaxType.IGST);
            existing.setIgstAmount(taxValue);
            existing.setSgstAmount(BigDecimal.ZERO);
            existing.setCgstAmount(BigDecimal.ZERO);
        }

        // 7. Net amount + rounding
        BigDecimal netAmount = taxableValue.add(taxValue);
        if (!dto.isIncludeFreightCharges()) {
            netAmount = netAmount.add(dto.getFreightAndForwardingCharges());
        }
        existing.setNetAmount(netAmount);

        BigDecimal rounded = BigDecimal.valueOf(Math.round(netAmount.doubleValue()));
        BigDecimal roundOff = netAmount.subtract(rounded);
        existing.setRoundOffAmount(roundOff);
        existing.setTotalPayableAmount(rounded);

        // 8. Logistics & remarks
        existing.setDeliveryAddress(dto.getDeliveryAddress());
        existing.setDispatchThrough(dto.getDispatchThrough());
        existing.setTransportMode(dto.getTransportMode());
        existing.setDeliveryDate(dto.getDeliveryDate());
        existing.setPackagingInstructions(dto.getPackagingInstructions());
        existing.setRemarks(dto.getRemarks());
        existing.setReference(dto.getReference());

        // 9. Save
        try {
            SalesOrder saved = salesOrderRepository.save(existing);
            logger.info("Sales Order updated successfully id={}, orderNumber={}", saved.getId(), saved.getOrderNumber());
            return salesOrderMapper.toDTO(saved);
        } catch (Exception e) {
            logger.error("Error while updating Sales Order: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update Sales Order, please try again later.");
        }
    }




    @Override
    public void deleteSalesOrder(Long id) {
        logger.info("Deleting Sales Order (soft delete) id={}", id);

        SalesOrder existing = salesOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sales Order not found with id " + id));

        existing.setDeletedDate(new java.util.Date());
        salesOrderRepository.save(existing);

        logger.info("Sales Order id={} marked as deleted", id);
    }

    @Override
    @Transactional
    public void salesOrderStatusChange(Long id, SalesOrderStatus newStatus, boolean isInventoryAction) throws Exception {
        // 1. Fetch sales order
        SalesOrder order = salesOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SalesOrder not found with id: " + id));

        // 2. Decide the next status based on current one
        SalesOrderStatus current = order.getStatus();
        if(current==SalesOrderStatus.DRAFT && newStatus==SalesOrderStatus.APPROVED && isInventoryAction){

            List<SalesOrderItem> salesOrderItemList = order.getItems();
            try {
                for (SalesOrderItem salesOrderItem : salesOrderItemList) {

                    InventoryRequest inventoryRequest = inventoryInstanceService.requestInstance(
                            salesOrderItem.getInventoryItem(),
                            salesOrderItem.getQty().doubleValue(),
                            InventoryRequestSource.SALES_ORDER, order.getId(),
                            "USER", "NEW ORDER");
                    salesOrderItem.setItemRequestId(inventoryRequest.getId());

                }
            }
            catch (Exception e){
                throw new Exception(e.getMessage());

            }

        }
        order.setStatus(newStatus);
        try{
            salesOrderRepository.save(order);
        }  catch (Exception e){
            throw new Exception(e.getMessage());

        }

        // 5. Log / audit
        logger.info("SalesOrder {} status changed from {} to {}", id, current, newStatus);
    }




}
