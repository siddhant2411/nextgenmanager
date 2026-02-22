package com.nextgenmanager.nextgenmanager.common.repository;

import com.nextgenmanager.nextgenmanager.common.model.UserRoleMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface UserRoleMapRepository extends JpaRepository<UserRoleMap,Long>  {
    @Transactional
    void deleteByAppUser_Id(Long appUserId);

    boolean existsByRole_Id(Long roleId);
}
