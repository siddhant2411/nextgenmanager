package com.nextgenmanager.nextgenmanager.production.service;


import com.nextgenmanager.nextgenmanager.production.dto.ProductionJobResponseDTO;
import com.nextgenmanager.nextgenmanager.production.model.ProductionJob;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.List;

public interface ProductionJobService {

    public ProductionJobResponseDTO getProductionJobById(int id);

    public ProductionJob getProductionJobEntityById(int id);

    public Page<ProductionJobResponseDTO> getProductionJobList(int page, int size, String sortBy, String sortDir, String search);

    public ProductionJobResponseDTO createProductionJob(ProductionJob productionJob);

    public ProductionJobResponseDTO updateProductionJob(int id,ProductionJob productionJob);

    public void deleteProductionJob(int id);

}
