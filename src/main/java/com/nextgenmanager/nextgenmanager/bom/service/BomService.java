package com.nextgenmanager.nextgenmanager.bom.service;

import com.nextgenmanager.nextgenmanager.bom.dto.*;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.model.BomPosition;
import com.nextgenmanager.nextgenmanager.common.dto.FilterRequest;
import com.nextgenmanager.nextgenmanager.common.model.FileAttachment;
import io.minio.GetObjectResponse;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.util.List;
import java.util.Map;

public interface BomService {

    public Bom addBom(Bom bom);

    public Bom getBom(int id);

    public BomDTO getBomDTO(int id);

    public Bom deleteBom(int id);

    public Bom editBom(int bomId,Bom bom);

    public Page<Bom> getAllBom(int page, int size, String sortBy, String sortDir, String query);

    public void saveAttachment(int id, MultipartFile bomAttachment) throws IOException;

    public UrlResource getAttachmentById(Long fileId) throws MalformedURLException;

    public void deleteAttachment(int bomId ,Long fileId) throws Exception;

    public List<Bom> getBomByParentInventoryItem(int id);


    public Page<BomListDTO> filterBom(FilterRequest request);

    public Page<BomListDTO> searchActiveBom(String request);

    public List<BomPosition> getBomPositions(int bomId);

    public List<BomPositionDTO> getBomPositionsDTO(int bomId);

    public BigDecimal calculateBomCost(int bomId);

    BomDTO changeBomStatus(int bomId,BomStatusChangeRequest bomStatusChangeRequest);

    List<FileAttachment> getAttachments(int bomId);


    FileAttachment getAttachment(long fileId);

    void uploadFile(int bomId, MultipartFile file) throws Exception;

    public GetObjectResponse downloadBomAttachment(Long fileId);

    BOMRoutingMapper duplicateBom(int bomId);

    public Map<Integer, RollupRow> getRolledUpQuantity(int bomId);

    public BomDTO getActiveBomByParentInventoryItem(int id);

    public List<BomDTO> getBomHistoryByParentInventoryItem(int id);
}
