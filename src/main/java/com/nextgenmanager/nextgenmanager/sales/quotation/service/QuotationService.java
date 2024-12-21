package com.nextgenmanager.nextgenmanager.sales.quotation.service;

import com.nextgenmanager.nextgenmanager.sales.quotation.model.Quotation;

import java.util.List;

public interface QuotationService {

    public Quotation getQuotationById(int id);

    public List<Quotation>  getQuotationList();

    public Quotation createQuotation(Quotation quotation);

    public Quotation updateQuotation(Quotation updatedQuotation,int id);

    public void deleteQuotation(int id);


}
