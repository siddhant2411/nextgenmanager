package com.nextgenmanager.nextgenmanager.production.service;



import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.production.dto.ProductionJobResponseDTO;
import com.nextgenmanager.nextgenmanager.production.mapper.ProductionJobResponseMapper;
import com.nextgenmanager.nextgenmanager.production.model.ProductionJob;
import com.nextgenmanager.nextgenmanager.production.repository.ProductionJobRepository;
import com.nextgenmanager.nextgenmanager.production.specifications.ProductionJobSpecification;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;


@Service
public class ProductionJobServiceImp implements ProductionJobService {

    private static final Logger logger = LoggerFactory.getLogger(ProductionJobServiceImp.class);

    @Autowired
    private ProductionJobRepository productionJobRepository;

    @Autowired
    private ProductionJobResponseMapper productionJobResponseMapper;

    @Override
    public ProductionJobResponseDTO getProductionJobById(int id) {
        logger.debug("Fetching Production job for ID: {}", id);
        ProductionJob productionJob = productionJobRepository.findById(id)
                .filter(job -> job.getDeletedDate() == null) // Check if deletedDate is null
                .orElseThrow(() -> {
                    logger.error("Production job not found or has been deleted for ID: {}", id);
                    return new ResourceNotFoundException("Production job not found or has been deleted for ID: " + id);
                });
        return productionJobResponseMapper.toDTO(productionJob);
    }

    @Override
    public ProductionJob getProductionJobEntityById(int id) {
        logger.debug("Fetching Production job for ID: {}", id);
        return productionJobRepository.findById(id)
                .filter(job -> job.getDeletedDate() == null) // Check if deletedDate is null
                .orElseThrow(() -> {
                    logger.error("Production job not found or has been deleted for ID: {}", id);
                    return new ResourceNotFoundException("Production job not found or has been deleted for ID: " + id);
                });
    }


    @Override
    public Page<ProductionJobResponseDTO> getProductionJobList(int page, int size, String sortBy, String sortDir, String search) {
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

        return productionJobRepository.findAll(spec, pageable).map(productionJob -> productionJobResponseMapper.toDTO(productionJob));
    }


    @Override
    @Transactional
    public ProductionJobResponseDTO createProductionJob(ProductionJob productionJob) {

        try {
            logger.debug("Creating new Production job: {}", productionJob);
            ProductionJob savedProductionJob = productionJobRepository.save(productionJob);
            logger.info("Successfully created Production job with ID: {}", savedProductionJob.getId());
            return productionJobResponseMapper.toDTO(savedProductionJob);
        } catch (Exception e) {
            logger.error("Something went wrong while creating Production job: " + e.getMessage());
            throw new RuntimeException(e);
        }


    }

    @Override
    public ProductionJobResponseDTO updateProductionJob(int id, ProductionJob productionJob) {
        logger.debug("Attempting to update Production job with ID: {}", id);

        productionJobRepository.findById(id)
                .orElseThrow(() -> {
                    logger.error("Production job not found for update, ID: {}", id);
                    return new ResourceNotFoundException("Production job not found for ID: " + id);
                });

        try {
            logger.debug("Updating  Production job with Id: {}", id);
            ProductionJob updatedProductionJob = productionJobRepository.save(productionJob);
            logger.info("Successfully updated Production job with ID: {}", updatedProductionJob.getId());
            return productionJobResponseMapper.toDTO(updatedProductionJob);
        } catch (Exception e) {
            logger.error("Something went wrong while updatedProductionJob Production job with id: {}" + e.getMessage(), id);
            throw new RuntimeException(e);
        }

    }

    @Override
    public void deleteProductionJob(int id) {
        logger.debug("Attempting to soft delete Production job with ID: {}", id);
        ProductionJob productionJob = productionJobRepository.findById(id)
                .filter(job -> job.getDeletedDate() == null) // Check if deletedDate is null
                .orElseThrow(() -> {
                    logger.error("Production job not found or has been deleted for ID: {}", id);
                    return new ResourceNotFoundException("Production job not found or has been deleted for ID: " + id);
                });
        productionJob.setDeletedDate(new Date());
        productionJobRepository.save(productionJob);
        logger.info("Successfully soft deleted Production job with ID: {}", id);
    }
}