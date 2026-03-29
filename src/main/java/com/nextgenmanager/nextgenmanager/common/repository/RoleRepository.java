package com.nextgenmanager.nextgenmanager.common.repository;

import com.nextgenmanager.nextgenmanager.common.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
     Role findByRoleName(String roleName);
     Optional<Role> findByRoleNameAndDeletedDateIsNull(String roleName);
     Optional<Role> findByIdAndDeletedDateIsNull(Long id);
     List<Role> findAllByDeletedDateIsNullOrderByRoleNameAsc();
}
