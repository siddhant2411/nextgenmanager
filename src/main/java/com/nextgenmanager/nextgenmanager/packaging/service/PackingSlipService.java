package com.nextgenmanager.nextgenmanager.packaging.service;

import com.nextgenmanager.nextgenmanager.packaging.dto.PackageBoxCreateRequest;
import com.nextgenmanager.nextgenmanager.packaging.dto.PackageBoxDto;
import com.nextgenmanager.nextgenmanager.packaging.dto.PackingSlipCreateRequest;
import com.nextgenmanager.nextgenmanager.packaging.dto.PackingSlipDto;
import com.nextgenmanager.nextgenmanager.packaging.model.PackingSlipStatus;

import java.util.List;

public interface PackingSlipService {

    List<PackingSlipDto> list(PackingSlipStatus status, Long salesOrderId);

    PackingSlipDto get(Long id);

    PackingSlipDto createFromPickList(PackingSlipCreateRequest request);

    PackageBoxDto addBox(Long slipId, PackageBoxCreateRequest request);

    PackingSlipDto pack(Long slipId);

    PackingSlipDto close(Long slipId);

    void cancel(Long slipId);
}
