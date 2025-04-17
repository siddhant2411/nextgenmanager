package com.nextgenmanager.nextgenmanager.production.service;


import com.nextgenmanager.nextgenmanager.production.model.ProductionJob;
import org.springframework.stereotype.Service;

import java.util.List;

public interface ProductionJobService {

    public ProductionJob getProductionJobById(int id);

    public List<ProductionJob> getProductionJobList();

    public ProductionJob createProductionJob(ProductionJob productionJob);

    public ProductionJob updateProductionJob(int id,ProductionJob productionJob);

    public void deleteProductionJob(int id);

}
