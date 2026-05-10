package com.nextgenmanager.nextgenmanager.production.repository.workcenter;

import com.nextgenmanager.nextgenmanager.production.model.workCenter.LaborRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LaborRoleRepository extends JpaRepository<LaborRole, Long>, JpaSpecificationExecutor<LaborRole> {

    Optional<LaborRole> findByRoleCodeAndDeletedDateIsNull(String roleCode);

    boolean existsByRoleCodeAndDeletedDateIsNull(String roleCode);
}
