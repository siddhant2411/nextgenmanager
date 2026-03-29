package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.production.dto.LaborRoleResponseDTO;
import com.nextgenmanager.nextgenmanager.production.model.LaborRole;
import org.springframework.data.domain.Page;

public interface LaborRoleService {

    LaborRoleResponseDTO getById(Long id);

    LaborRole getEntityById(Long id);

    Page<LaborRoleResponseDTO> getAll(int page, int size, String sortBy, String sortDir, String search);

    LaborRoleResponseDTO create(LaborRole laborRole);

    LaborRoleResponseDTO update(Long id, LaborRole laborRole);

    void delete(Long id);
}
