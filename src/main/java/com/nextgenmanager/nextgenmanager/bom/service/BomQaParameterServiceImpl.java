package com.nextgenmanager.nextgenmanager.bom.service;

import com.nextgenmanager.nextgenmanager.bom.dto.BomQaParameterDTO;
import com.nextgenmanager.nextgenmanager.bom.dto.BomQaParameterRequestDTO;
import com.nextgenmanager.nextgenmanager.bom.model.BomQaParameter;
import com.nextgenmanager.nextgenmanager.bom.repository.BomQaParameterRepository;
import com.nextgenmanager.nextgenmanager.bom.model.routing.RoutingOperation;
import com.nextgenmanager.nextgenmanager.bom.repository.routing.RoutingOperationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BomQaParameterServiceImpl implements BomQaParameterService {

    @Autowired
    private BomQaParameterRepository repository;

    @Autowired
    private RoutingOperationRepository routingOperationRepository;

    @Override
    public List<BomQaParameterDTO> getParametersForOperation(Long routingOperationId) {
        return repository.findByRoutingOperationIdAndDeletedDateIsNull(routingOperationId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public BomQaParameterDTO addParameter(Long routingOperationId, BomQaParameterRequestDTO request) {
        RoutingOperation op = routingOperationRepository.findById(routingOperationId)
                .orElseThrow(() -> new EntityNotFoundException("RoutingOperation not found: " + routingOperationId));

        BomQaParameter param = BomQaParameter.builder()
                .routingOperation(op)
                .name(request.getName())
                .parameterType(request.getParameterType())
                .minValue(request.getMinValue())
                .maxValue(request.getMaxValue())
                .unit(request.getUnit())
                .critical(request.getCritical() != null ? request.getCritical() : false)
                .build();

        return toDTO(repository.save(param));
    }

    @Override
    @Transactional
    public BomQaParameterDTO updateParameter(Long parameterId, BomQaParameterRequestDTO request) {
        BomQaParameter param = repository.findById(parameterId)
                .orElseThrow(() -> new EntityNotFoundException("BomQaParameter not found: " + parameterId));

        param.setName(request.getName());
        param.setParameterType(request.getParameterType());
        param.setMinValue(request.getMinValue());
        param.setMaxValue(request.getMaxValue());
        param.setUnit(request.getUnit());
        param.setCritical(request.getCritical() != null ? request.getCritical() : false);

        return toDTO(repository.save(param));
    }

    @Override
    @Transactional
    public void deleteParameter(Long parameterId) {
        BomQaParameter param = repository.findById(parameterId)
                .orElseThrow(() -> new EntityNotFoundException("BomQaParameter not found: " + parameterId));
        param.setDeletedDate(new Date());
        repository.save(param);
    }

    private BomQaParameterDTO toDTO(BomQaParameter p) {
        return BomQaParameterDTO.builder()
                .id(p.getId())
                .routingOperationId(p.getRoutingOperation().getId())
                .name(p.getName())
                .parameterType(p.getParameterType())
                .minValue(p.getMinValue())
                .maxValue(p.getMaxValue())
                .unit(p.getUnit())
                .critical(p.getCritical())
                .creationDate(p.getCreationDate())
                .build();
    }
}
