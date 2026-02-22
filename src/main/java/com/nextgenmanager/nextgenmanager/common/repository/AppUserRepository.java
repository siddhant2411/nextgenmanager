package com.nextgenmanager.nextgenmanager.common.repository;

import com.nextgenmanager.nextgenmanager.common.model.AppUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
   @EntityGraph(attributePaths = {"userRoleMaps", "userRoleMaps.role"})
   Optional<AppUser> findByUsernameAndDeletedDateIsNull(String username);

   Optional<AppUser> findByEmailAndDeletedDateIsNull(String email);

   @EntityGraph(attributePaths = {"userRoleMaps", "userRoleMaps.role"})
   java.util.List<AppUser> findAllByDeletedDateIsNullOrderByUsernameAsc();

   @EntityGraph(attributePaths = {"userRoleMaps", "userRoleMaps.role"})
   Optional<AppUser> findByIdAndDeletedDateIsNull(Long id);
}
