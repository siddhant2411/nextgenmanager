package com.nextgenmanager.nextgenmanager.Inventory.service;

import com.nextgenmanager.nextgenmanager.Inventory.dto.CreateGRNRequest;
import com.nextgenmanager.nextgenmanager.Inventory.dto.GRNResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface GRNService {

    /** Creates a GRN, posts a PRODUCE stock ledger entry for each accepted line, and updates the PO status. */
    GRNResponseDTO createGRN(CreateGRNRequest request);

    GRNResponseDTO getGRN(Long grnId);

    List<GRNResponseDTO> getGRNsByPurchaseOrder(Long purchaseOrderId);

    Page<GRNResponseDTO> searchGRNs(Long poId, String status, Long vendorId, String grnNumber, Pageable pageable);
}
