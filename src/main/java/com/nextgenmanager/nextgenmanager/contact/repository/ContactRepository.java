package com.nextgenmanager.nextgenmanager.contact.repository;

import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.contact.model.ContactType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContactRepository extends JpaRepository<Contact, Integer> {

    Optional<Contact> findByIdAndDeletedDateIsNull(int id);

    Optional<Contact> findByContactCodeAndDeletedDateIsNull(String contactCode);

    /** Full search with optional filters — used on the Contact list page.
     *  When filtering by VENDOR or CUSTOMER, contacts of type BOTH are also included. */
    @Query("SELECT c FROM Contact c WHERE c.deletedDate IS NULL " +
           "AND (:type IS NULL OR c.contactType = :type OR " +
           "     (c.contactType = 'BOTH' AND :type IS NOT NULL)) " +
           "AND (COALESCE(:query, '') = '' OR " +
           "     LOWER(c.companyName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "     LOWER(c.contactCode) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "     LOWER(c.gstNumber)   LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "     LOWER(c.phone)       LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "     LOWER(c.email)       LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Contact> searchContacts(
            @Param("query") String query,
            @Param("type") ContactType type,
            Pageable pageable);

    /**
     * Lightweight search for dropdowns — returns only contacts matching the query
     * and optionally filtered by type. Limited to top 20 for performance.
     */
    @Query("SELECT c FROM Contact c WHERE c.deletedDate IS NULL " +
           "AND (:type IS NULL OR c.contactType = :type OR c.contactType = 'BOTH') " +
           "AND (LOWER(c.companyName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "     LOWER(c.contactCode) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "     LOWER(c.gstNumber)   LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "ORDER BY c.companyName ASC")
    List<Contact> searchForDropdown(
            @Param("query") String query,
            @Param("type") ContactType type,
            Pageable pageable);

    /** Count contacts by type — for dashboard stats. */
    long countByContactTypeAndDeletedDateIsNull(ContactType contactType);

    boolean existsByContactCodeAndDeletedDateIsNull(String contactCode);

    /** Last used contactCode prefix to generate next sequential code. */
    @Query("SELECT c.contactCode FROM Contact c WHERE c.contactCode LIKE :prefix% " +
           "AND c.deletedDate IS NULL ORDER BY c.contactCode DESC")
    List<String> findLastCodeByPrefix(@Param("prefix") String prefix, Pageable pageable);
}
