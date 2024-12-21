package com.nextgenmanager.nextgenmanager.contact.repository;

import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContactRepository extends JpaRepository<Contact, Integer> {

    @Query("SELECT c FROM Contact c WHERE c.deletedDate IS NULL AND" +
            "(COALESCE(:companyName, '') = '' OR LOWER(c.companyName) LIKE LOWER(CONCAT('%',:companyName,'%'))) AND " +
            "(COALESCE(:gstNumber, '') = '' OR LOWER(c.gstNumber) LIKE LOWER(CONCAT('%',:gstNumber,'%'))) AND " +
            "(COALESCE(:state, '') = '' OR c.addresses IS NOT EMPTY AND EXISTS (" +
            "SELECT a FROM ContactAddress a WHERE LOWER(a.state) LIKE LOWER(CONCAT('%',:state,'%')) AND a.contact = c))")
    Page<Contact> searchContacts(@Param("companyName") String companyName,
                                 @Param("gstNumber") String gstNumber,
                                 @Param("state") String state,
                                 Pageable pageable);

    @Query(value = "SELECT * FROM Contact c WHERE c.id = :id AND c.deletedDate IS NULL", nativeQuery = true)
    InventoryItem findByActiveId(@Param("id") int id);
}
