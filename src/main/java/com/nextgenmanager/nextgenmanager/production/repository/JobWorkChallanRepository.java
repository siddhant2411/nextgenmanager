package com.nextgenmanager.nextgenmanager.production.repository;

import com.nextgenmanager.nextgenmanager.production.enums.ChallanStatus;
import com.nextgenmanager.nextgenmanager.production.model.JobWorkChallan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobWorkChallanRepository extends JpaRepository<JobWorkChallan, Long> {

    Optional<JobWorkChallan> findByChallanNumberAndDeletedDateIsNull(String challanNumber);

    List<JobWorkChallan> findByDeletedDateIsNullOrderByCreationDateDesc();

    List<JobWorkChallan> findByVendor_IdAndDeletedDateIsNullOrderByCreationDateDesc(int vendorId);

    List<JobWorkChallan> findByWorkOrder_IdAndDeletedDateIsNull(int workOrderId);

    List<JobWorkChallan> findByStatusAndDeletedDateIsNull(ChallanStatus status);

    /** Challans dispatched but not yet returned, past the expected return date (overdue). */
    @Query("SELECT c FROM JobWorkChallan c WHERE c.status IN ('DISPATCHED','PARTIALLY_RECEIVED') " +
           "AND c.expectedReturnDate < :today AND c.deletedDate IS NULL")
    List<JobWorkChallan> findOverdue(@Param("today") Date today);

    /** For challan number generation — get the last number in the current financial year prefix. */
    @Query("SELECT c.challanNumber FROM JobWorkChallan c WHERE c.challanNumber LIKE :prefix% " +
           "ORDER BY c.challanNumber DESC")
    List<String> findLastChallanNumberByPrefix(@Param("prefix") String prefix);
}
