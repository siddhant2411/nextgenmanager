package com.nextgenmanager.nextgenmanager.marketing.quotation.service;

import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryInstanceRepository;
import com.nextgenmanager.nextgenmanager.marketing.quotation.dto.QuotationDisplayDTO;
import com.nextgenmanager.nextgenmanager.marketing.quotation.model.Quotation;
import com.nextgenmanager.nextgenmanager.marketing.quotation.model.QuotationProducts;
import com.nextgenmanager.nextgenmanager.marketing.quotation.repository.QuotationRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class QuotationServiceImp implements QuotationService{

    @Autowired
    private QuotationRepository quotationRepository;

    @Autowired
    private InventoryInstanceRepository inventoryInstanceRepository;
    Logger logger = LoggerFactory.getLogger(QuotationServiceImp.class);

    @Override
    public Quotation getQuotationById(int id) {


        logger.info("Fetching Quotation with ID: {}", id);

        Quotation quotation = quotationRepository.findByActiveId(id);

        if (quotation == null) {
            logger.error("Quotation not found with ID: {}", id);
            throw new RuntimeException("Quotation not found with ID: " + id);
        }

        logger.info("Quotation fetched successfully with ID: {}", id);
        return quotation;
    }



    @Override
    public List<Quotation> getQuotationList() {
        logger.info("Fetching all quotations");
        return quotationRepository.findAll();
    }

    @Override
    public Page<QuotationDisplayDTO> getQuotationDisplayList(
            int page, int size, String sortBy, String sortDir,
            String qtnNoFilter, LocalDate qtnDateFilter, LocalDate enqDateFilter,
            String enqNoFilter, String companyNameFilter, BigDecimal netAmountFilter,
            BigDecimal totalAmountFilter) {

        Pageable pageable = PageRequest.of(page, size,
                sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending());

        Page<Object[]> activeQuotations = quotationRepository.getActiveQuotation(
                pageable, companyNameFilter, qtnNoFilter, qtnDateFilter, enqDateFilter,
                enqNoFilter, netAmountFilter, totalAmountFilter);

        return activeQuotations.map(record -> {
            try {
                return new QuotationDisplayDTO(
                        record[0] != null ? ((Number) record[0]).intValue() : null,
                        record[1] != null ? record[1].toString() : null,
                        record[2] != null ? ((java.sql.Date) record[2]).toLocalDate() : null,
                        record[3] != null ? record[3].toString() : null,
                        record[4] != null ? ((java.sql.Date) record[4]).toLocalDate() : null,
                        record[5] != null ? record[5].toString() : null,
                        record[6] != null ? (BigDecimal) record[6] : null,
                        record[7] != null ? (BigDecimal) record[7] : null
                );
            } catch (Exception e) {
                logger.error("Error mapping quotation data for record {}: {}", record, e.getMessage());
                throw new RuntimeException("Data mapping error", e);
            }
        });
    }


    @Override
    @Transactional
    public Quotation createQuotation(Quotation quotation) {
        try {
            if (quotation.getQuotationProducts() != null) {
                quotation.getQuotationProducts().forEach(product -> product.setQuotation(quotation));
            }

            List<QuotationProducts> finalQuotationProducts = new ArrayList<>();
            double netAmount = 0, gstAmount, roundOff, discountAmount, discountedAmount;
            long totalAmount;

            for (QuotationProducts quotationProduct : quotation.getQuotationProducts()) {
                double discount = quotationProduct.getDiscountPercentage();

                if (quotationProduct.getInventoryItem() != null && quotationProduct.getInventoryItem().getInventoryItemId() <= 0) {
                    quotationProduct.setInventoryItem(null);
                }

                double pricePerUnit = quotationProduct.getPricePerUnit();
                double totalUnitPrice = pricePerUnit - pricePerUnit * discount * 0.01;
                quotationProduct.setId(0);

                quotationProduct.setUnitPriceAfterDiscount(BigDecimal.valueOf(totalUnitPrice));
                quotationProduct.setTotalAmountOfProduct(BigDecimal.valueOf(quotationProduct.getQty() * totalUnitPrice));
                netAmount += quotationProduct.getQty() * totalUnitPrice;
                finalQuotationProducts.add(quotationProduct);
            }

            // 🔹 Ensure Hibernate recognizes new list
            quotation.setQuotationProducts(new ArrayList<>(finalQuotationProducts));

            quotation.setNetAmount(BigDecimal.valueOf(netAmount));

            discountAmount = netAmount * quotation.getDiscountPercentage().doubleValue() * 0.01;
            discountedAmount = netAmount - discountAmount;
            BigDecimal pandfcharges = quotation.getPandfcharges();
            double taxableAmount = discountedAmount + pandfcharges.doubleValue();
            gstAmount = taxableAmount * quotation.getGstPercentage().doubleValue() * 0.01;

            totalAmount = Math.round(gstAmount + taxableAmount);
            roundOff = totalAmount - (gstAmount + taxableAmount);
            quotation.setTotalAmount(BigDecimal.valueOf(totalAmount));
            quotation.setRoundOff(BigDecimal.valueOf(roundOff));
            quotation.setGstAmount(BigDecimal.valueOf(gstAmount));
            quotation.setDiscountAmount(BigDecimal.valueOf(discountAmount));

            Quotation savedQuotation = quotationRepository.save(quotation);
            logger.info("Quotation created successfully with ID: {}", savedQuotation.getId());
            return savedQuotation;
        } catch (Exception e) {
            logger.error("Error creating quotation: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create quotation.");
        }
    }



    @Override
    public Quotation updateQuotation(Quotation updatedQuotation, int id) {
        logger.info("Updating Quotation with ID: {}", id);

        Quotation existingQuotation = quotationRepository.findById(id).orElseThrow(() -> {
            logger.error("Quotation not found with ID: {}", id);
            return new RuntimeException("Quotation not found with ID: " + id);
        });

        try {
            updatedQuotation.setId(existingQuotation.getId());
            List<QuotationProducts> finalQuotationProducts = new ArrayList<>();
            double netAmount= 0;
            double gstAmount,roundOff;
            long totalAmount;
            for(QuotationProducts quotationProduct: updatedQuotation.getQuotationProducts()){
                double discount =  quotationProduct.getDiscountPercentage();
                if(quotationProduct.getInventoryItem()!= null && quotationProduct.getInventoryItem().getInventoryItemId()<=0) {
                    quotationProduct.setInventoryItem(null);

                }
                double pricePerUnit = quotationProduct.getPricePerUnit();
                double totalUnitPrice = pricePerUnit - pricePerUnit * discount * 0.01;
                quotationProduct.setUnitPriceAfterDiscount(BigDecimal.valueOf(totalUnitPrice));
                quotationProduct.setTotalAmountOfProduct(BigDecimal.valueOf(quotationProduct.getQty() * totalUnitPrice));
                netAmount += quotationProduct.getQty() * totalUnitPrice;
                finalQuotationProducts.add(quotationProduct);
            }
            updatedQuotation.setQuotationProducts(finalQuotationProducts);
            updatedQuotation.setNetAmount(BigDecimal.valueOf(netAmount));
            gstAmount = netAmount*updatedQuotation.getGstPercentage().doubleValue()*0.01;
            totalAmount = Math.round(gstAmount+netAmount);
            roundOff =  totalAmount-(gstAmount+netAmount);
            updatedQuotation.setTotalAmount(BigDecimal.valueOf(totalAmount));
            updatedQuotation.setRoundOff(BigDecimal.valueOf(roundOff));
            updatedQuotation.setGstAmount(BigDecimal.valueOf(gstAmount));

            Quotation savedQuotation = quotationRepository.save(updatedQuotation);
            logger.info("Quotation created successfully with ID: {}", savedQuotation.getId());
            return savedQuotation;
        } catch (Exception e) {
            logger.error("Error updating quotation: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update quotation.");
        }
    }

    @Override
    public void deleteQuotation(int id) {
        logger.info("Deleting Quotation with ID: {}", id);

        Quotation quotation = quotationRepository.findById(id).orElseThrow(() -> {
            logger.error("Quotation not found with ID: {}", id);
            return new RuntimeException("Quotation not found with ID: " + id);
        });

        quotation.setDeletedDate(new Date());
        quotationRepository.save(quotation);
        logger.info("Quotation deleted (soft delete) successfully with ID: {}", id);
    }
}
