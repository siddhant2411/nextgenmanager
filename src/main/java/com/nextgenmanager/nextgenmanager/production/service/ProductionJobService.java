package com.nextgenmanager.nextgenmanager.production.service;


import com.nextgenmanager.nextgenmanager.production.model.ProductionJob;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.List;

public interface ProductionJobService {

    public ProductionJob getProductionJobById(int id);

    public Page<ProductionJob> getProductionJobList(int page, int size, String sortBy, String sortDir, String search);

    public ProductionJob createProductionJob(ProductionJob productionJob);

    public ProductionJob updateProductionJob(int id,ProductionJob productionJob);

    public void deleteProductionJob(int id);

}
