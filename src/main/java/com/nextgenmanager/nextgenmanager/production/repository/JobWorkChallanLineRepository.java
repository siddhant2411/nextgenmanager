package com.nextgenmanager.nextgenmanager.production.repository;

import com.nextgenmanager.nextgenmanager.production.model.JobWorkChallanLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobWorkChallanLineRepository extends JpaRepository<JobWorkChallanLine, Long> {

    List<JobWorkChallanLine> findByChallan_Id(Long challanId);
}
