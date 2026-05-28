package com.nextgenmanager.nextgenmanager.purchase.repository;

import com.nextgenmanager.nextgenmanager.purchase.model.DebitNote;
import com.nextgenmanager.nextgenmanager.purchase.model.DebitNoteStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DebitNoteRepository extends JpaRepository<DebitNote, Long> {

    Optional<DebitNote> findByIdAndDeletedDateIsNull(Long id);

    Page<DebitNote> findByDeletedDateIsNull(Pageable pageable);

    List<DebitNote> findByVendor_IdAndDeletedDateIsNull(int vendorId);

    List<DebitNote> findByGrn_IdAndDeletedDateIsNull(Long grnId);

    List<DebitNote> findByPurchaseOrder_IdAndDeletedDateIsNull(Long poId);

    boolean existsByDebitNoteNumber(String debitNoteNumber);

    List<DebitNote> findByStatusAndDeletedDateIsNull(DebitNoteStatus status);
}
