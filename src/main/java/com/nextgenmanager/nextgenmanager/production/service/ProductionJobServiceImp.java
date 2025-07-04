package com.nextgenmanager.nextgenmanager.production.service;



import com.nextgenmanager.nextgenmanager.production.model.ProductionJob;
import com.nextgenmanager.nextgenmanager.production.repository.ProductionJobRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;


@Service
public class ProductionJobServiceImp implements ProductionJobService{

    private static final Logger logger = LoggerFactory.getLogger(ProductionJobServiceImp.class);

    @Autowired
    private ProductionJobRepository productionJobRepository;

    @Override
    public ProductionJob getProductionJobById(int id) {
        logger.debug("Fetching Production job for ID: {}", id);

        return productionJobRepository.findById(id)
                .filter(job -> job.getDeletedDate() == null) // Check if deletedDate is null
                .orElseThrow(() -> {
                    logger.error("Production job not found or has been deleted for ID: {}", id);
                    return new RuntimeException("Production job not found or has been deleted for ID: " + id);
                });
    }


    @Override
    public Page<ProductionJob> getProductionJobList(int page, int size, String sortBy, String sortDir, String search) {
        logger.debug("Fetching paginated ProductionJob list");

        Pageable pageable = PageRequest.of(page, size,
                sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending());

        Specification<ProductionJob> spec = (root, query, cb) -> {
            Predicate predicate = cb.isNull(root.get("deletedDate"));
            if (search != null && !search.isEmpty()) {
                Predicate jobNameMatch = cb.like(cb.lower(root.get("jobName")), "%" + search.toLowerCase() + "%");
                predicate = cb.and(predicate, jobNameMatch);
            }
            return predicate;
        };

        return productionJobRepository.findAll(spec, pageable);
    }


    @Override
    public ProductionJob createProductionJob(ProductionJob productionJob) {

        logger.debug("Creating new Production job: {}", productionJob);
        ProductionJob savedProductionJob = productionJobRepository.save(productionJob);
        logger.info("Successfully created Production job with ID: {}", savedProductionJob.getId());
        return savedProductionJob;
    }

    @Override
    public ProductionJob updateProductionJob(int id,ProductionJob productionJob) {
        logger.info("Attempting to update Production job with ID: {}", id);

        ProductionJob existingProductionJob = productionJobRepository.findById(id)
                .orElseThrow(() -> {
                    logger.error("Production job not found for update, ID: {}", id);
                    return new RuntimeException("Production job not found for ID: " + id);
                });
        productionJob.setId(id);
        return productionJobRepository.save(productionJob);
    }

    @Override
    public void deleteProductionJob(int id) {
        logger.debug("Attempting to soft delete Production job with ID: {}", id);
        ProductionJob productionJob = getProductionJobById(id);
        productionJob.setDeletedDate(new Date());
        productionJobRepository.save(productionJob);
        logger.info("Successfully soft deleted Production job with ID: {}", id);
    }
}
