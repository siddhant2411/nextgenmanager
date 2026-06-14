package com.nextgenmanager.nextgenmanager.accounting.coa.repository;

import com.nextgenmanager.nextgenmanager.accounting.coa.model.AccountGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AccountGroupRepository extends JpaRepository<AccountGroup, Long> {

    Optional<AccountGroup> findByCodeAndDeletedDateIsNull(String code);

    List<AccountGroup> findByDeletedDateIsNullOrderBySortOrder();

    /** Fetch full tree in one query; caller builds the hierarchy in Java. */
    @Query("SELECT g FROM AccountGroup g LEFT JOIN FETCH g.parentGroup WHERE g.deletedDate IS NULL ORDER BY g.sortOrder")
    List<AccountGroup> findAllWithParent();

    boolean existsByCodeAndDeletedDateIsNull(String code);
}
