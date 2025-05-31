package com.nextgenmanager.nextgenmanager.bom.service;

import com.nextgenmanager.nextgenmanager.bom.dto.BomDTO;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.model.BomAttachment;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderProductionTemplate;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;
import java.util.Optional;

public interface BomService {

    public Bom addBom(Bom bom);

    public Bom getBom(int id);

    public Bom deleteBom(int id);

    public Bom editBom(Bom bom);

    public Page<BomDTO> getAllBom(int page, int size, String sortBy, String sortDir, String query);

    public void saveAttachment(int id, MultipartFile bomAttachment) throws IOException;

    public UrlResource getAttachmentById(Long fileId) throws MalformedURLException;

    public void deleteAttachment(Long fileId) throws IOException;

    public List<Bom> getBomByParentInventoryItem(int id);

    public WorkOrderProductionTemplate getBomWOTemplateByBomId(int id);

}
