package com.nextgenmanager.nextgenmanager.production.repository;


import com.nextgenmanager.nextgenmanager.production.model.ProductionJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductionJobRepository extends JpaRepository<ProductionJob, Integer>, JpaSpecificationExecutor<ProductionJob> {
}

