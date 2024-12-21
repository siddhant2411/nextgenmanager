package com.nextgenmanager.nextgenmanager.sales.quotation.service;

import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstance;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryInstanceRepository;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.contact.model.ContactAddress;
import com.nextgenmanager.nextgenmanager.contact.model.ContactPersonDetail;
import com.nextgenmanager.nextgenmanager.sales.quotation.model.Quotation;
import com.nextgenmanager.nextgenmanager.sales.quotation.model.QuotationProducts;
import com.nextgenmanager.nextgenmanager.sales.quotation.repository.QuotationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
    public Quotation createQuotation(Quotation quotation) {
        try {
            if (quotation.getQuotationProducts() != null) {
                quotation.getQuotationProducts().forEach(product -> product.setQuotation(quotation));
            }
            List<QuotationProducts> finalQuotationProducts = new ArrayList<>();
            double netAmount= 0;
            double gstAmount,roundOff;
            long totalAmount;
            for(QuotationProducts quotationProduct: quotation.getQuotationProducts()){
                double discount =  quotationProduct.getDiscountPercentage();
                int inventoryItemId = quotationProduct.getInventoryInstance().getInventoryItem().getInventoryItemId();
                double sellPrice = inventoryInstanceRepository.findLatestInventoryInstance(inventoryItemId).getSellPricePerUnit();
                double totalUnitPrice = sellPrice - sellPrice * discount * 0.01;
                quotationProduct.setUnitPriceAfterDiscount(BigDecimal.valueOf(totalUnitPrice));
                quotationProduct.setTotalAmountOfProduct(BigDecimal.valueOf(quotationProduct.getQty()*totalUnitPrice));
                netAmount+= quotationProduct.getQty()*totalUnitPrice;
                finalQuotationProducts.add(quotationProduct);
            }
            quotation.setQuotationProducts(finalQuotationProducts);
            quotation.setNetAmount(BigDecimal.valueOf(netAmount));
            gstAmount = netAmount*quotation.getGstPercentage().doubleValue()*0.01;
            totalAmount = Math.round(gstAmount+netAmount);
            roundOff =  totalAmount-(gstAmount+netAmount);
            quotation.setTotalAmount(BigDecimal.valueOf(totalAmount));
            quotation.setRoundOff(BigDecimal.valueOf(roundOff));
            quotation.setGstAmount(BigDecimal.valueOf(gstAmount));

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
                int inventoryItemId = quotationProduct.getInventoryInstance().getInventoryItem().getInventoryItemId();
                double sellPrice = inventoryInstanceRepository.findLatestInventoryInstance(inventoryItemId).getSellPricePerUnit();
                double totalUnitPrice = sellPrice - sellPrice * discount * 0.01;
                quotationProduct.setUnitPriceAfterDiscount(BigDecimal.valueOf(totalUnitPrice));
                quotationProduct.setTotalAmountOfProduct(BigDecimal.valueOf(quotationProduct.getQty()*totalUnitPrice));
                netAmount+= quotationProduct.getQty()*totalUnitPrice;
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
