package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.production.dto.LaborRoleResponseDTO;
import com.nextgenmanager.nextgenmanager.production.mapper.LaborRoleMapper;
import com.nextgenmanager.nextgenmanager.production.model.LaborRole;
import com.nextgenmanager.nextgenmanager.production.repository.LaborRoleRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class LaborRoleServiceImpl implements LaborRoleService {

    @Autowired
    private LaborRoleRepository laborRoleRepository;

    @Autowired
    private LaborRoleMapper laborRoleMapper;

    @Override
    public LaborRoleResponseDTO getById(Long id) {
        return laborRoleMapper.toDTO(getActiveLaborRole(id));
    }

    @Override
    public LaborRole getEntityById(Long id) {
        return getActiveLaborRole(id);
    }

    @Override
    public Page<LaborRoleResponseDTO> getAll(int page, int size, String sortBy, String sortDir, String search) {
        Pageable pageable = PageRequest.of(page, size,
                sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending());

        Specification<LaborRole> spec = (root, query, cb) -> {
            Predicate predicate = cb.isNull(root.get("deletedDate"));
            if (search != null && !search.isEmpty()) {
                Predicate roleName = cb.like(cb.lower(root.get("roleName")), "%" + search.toLowerCase() + "%");
                Predicate roleCode = cb.like(cb.lower(root.get("roleCode")), "%" + search.toLowerCase() + "%");
                predicate = cb.and(predicate, cb.or(roleName, roleCode));
            }
            return predicate;
        };

        return laborRoleRepository.findAll(spec, pageable)
                .map(laborRoleMapper::toDTO);
    }

    @Override
    public LaborRoleResponseDTO create(LaborRole laborRole) {
        if (laborRoleRepository.existsByRoleCodeAndDeletedDateIsNull(laborRole.getRoleCode())) {
            throw new IllegalArgumentException("Labor role code already exists: " + laborRole.getRoleCode());
        }
        return laborRoleMapper.toDTO(laborRoleRepository.save(laborRole));
    }

    @Override
    public LaborRoleResponseDTO update(Long id, LaborRole updated) {
        LaborRole existing = getActiveLaborRole(id);

        if (!existing.getRoleCode().equals(updated.getRoleCode())
                && laborRoleRepository.existsByRoleCodeAndDeletedDateIsNull(updated.getRoleCode())) {
            throw new IllegalArgumentException("Labor role code already exists: " + updated.getRoleCode());
        }

        existing.setRoleCode(updated.getRoleCode());
        existing.setRoleName(updated.getRoleName());
        existing.setCostPerHour(updated.getCostPerHour());
        existing.setDescription(updated.getDescription());
        existing.setActive(updated.isActive());

        return laborRoleMapper.toDTO(laborRoleRepository.save(existing));
    }

    @Override
    public void delete(Long id) {
        LaborRole laborRole = getActiveLaborRole(id);
        laborRole.setDeletedDate(new Date());
        laborRoleRepository.save(laborRole);
    }

    private LaborRole getActiveLaborRole(Long id) {
        LaborRole laborRole = laborRoleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Labor role not found with id: " + id));
        if (laborRole.getDeletedDate() != null) {
            throw new ResourceNotFoundException("Labor role not found with id: " + id);
        }
        return laborRole;
    }
}
