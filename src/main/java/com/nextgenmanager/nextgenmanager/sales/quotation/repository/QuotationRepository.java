package com.nextgenmanager.nextgenmanager.sales.quotation.repository;

import com.nextgenmanager.nextgenmanager.sales.quotation.model.Quotation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface QuotationRepository extends JpaRepository<Quotation,Integer> {

    @Query(value = "select * from quotation q where q.id=:id AND q.deletedDate IS NULL", nativeQuery = true)
    public Quotation findByActiveId(int id);

}
