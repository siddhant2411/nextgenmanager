package com.nextgenmanager.nextgenmanager.bom.service;

import com.nextgenmanager.nextgenmanager.bom.dto.BomDTO;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import org.springframework.data.domain.Page;

public interface BomService {

    public Bom addBom(Bom bom);

    public Bom getBom(int id);

    public Bom deleteBom(int id);

    public Bom editBom(Bom bom);

    public Page<BomDTO> getAllBom(int page, int size, String sortBy, String sortDir, String query);

}
