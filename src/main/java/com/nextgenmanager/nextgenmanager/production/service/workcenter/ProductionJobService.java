package com.nextgenmanager.nextgenmanager.production.service.workcenter;


import com.nextgenmanager.nextgenmanager.production.dto.ProductionJobResponseDTO;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.ProductionJob;
import org.springframework.data.domain.Page;

public interface ProductionJobService {

    public ProductionJobResponseDTO getProductionJobById(int id);

    public ProductionJob getProductionJobEntityById(int id);

    public Page<ProductionJobResponseDTO> getProductionJobList(int page, int size, String sortBy, String sortDir, String search);

    public ProductionJobResponseDTO createProductionJob(ProductionJob productionJob);

    public ProductionJobResponseDTO updateProductionJob(int id,ProductionJob productionJob);

    public void deleteProductionJob(int id);

}
