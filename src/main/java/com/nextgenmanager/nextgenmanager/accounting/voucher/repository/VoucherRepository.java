package com.nextgenmanager.nextgenmanager.accounting.voucher.repository;

import com.nextgenmanager.nextgenmanager.accounting.voucher.model.Voucher;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherStatus;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {

    /** Detail fetch — JOIN FETCH lines, ledgerAccount, and period so no lazy init outside transaction. */
    @Query("SELECT v FROM Voucher v JOIN FETCH v.period JOIN FETCH v.lines l JOIN FETCH l.ledgerAccount " +
           "WHERE v.id = :id AND v.deletedDate IS NULL")
    Optional<Voucher> findByIdWithLines(@Param("id") Long id);

    /** List query — never touches lines collection (lazy safe). */
    List<Voucher> findByDeletedDateIsNullAndDateBetweenOrderByDateAscVoucherNumberAsc(
            LocalDate from, LocalDate to);

    /** Day Book: JOIN FETCH lines + ledgerAccount in one query to avoid N+1. */
    @Query("SELECT DISTINCT v FROM Voucher v JOIN FETCH v.lines l JOIN FETCH l.ledgerAccount " +
           "WHERE v.deletedDate IS NULL AND v.status = 'POSTED' " +
           "AND v.date BETWEEN :from AND :to " +
           "ORDER BY v.date ASC, v.voucherNumber ASC")
    List<Voucher> findPostedWithLinesByDateRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    List<Voucher> findByDeletedDateIsNullAndVoucherTypeAndDateBetweenOrderByDateAsc(
            VoucherType type, LocalDate from, LocalDate to);

    List<Voucher> findByDeletedDateIsNullAndStatusOrderByDateAscVoucherNumberAsc(VoucherStatus status);

    boolean existsBySourceDocTypeAndSourceDocIdAndDeletedDateIsNull(String docType, Long docId);

    Optional<Voucher> findBySourceDocTypeAndSourceDocIdAndDeletedDateIsNull(String docType, Long docId);
}
