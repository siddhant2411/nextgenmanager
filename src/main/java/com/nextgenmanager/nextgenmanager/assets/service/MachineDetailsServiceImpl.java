package com.nextgenmanager.nextgenmanager.assets.service;

import com.nextgenmanager.nextgenmanager.assets.dto.MachineDetailsResponseDTO;
import com.nextgenmanager.nextgenmanager.assets.mapper.MachineDetailsResponseMapper;
import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.assets.model.MachineStatusHistory;
import com.nextgenmanager.nextgenmanager.assets.repository.MachineDetailsRepository;
import com.nextgenmanager.nextgenmanager.assets.repository.MachineStatusHistoryRepository;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.common.dto.FilterCriteria;
import com.nextgenmanager.nextgenmanager.common.dto.FilterRequest;
import com.nextgenmanager.nextgenmanager.common.spec.GenericSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class MachineDetailsServiceImpl implements MachineDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(MachineDetailsServiceImpl.class);

    @Autowired
    private MachineDetailsRepository machineDetailsRepository;


    @Autowired
    private MachineDetailsResponseMapper machineDetailsResponseMapper;

    @Autowired
    private MachineStatusHistoryRepository machineStatusHistoryRepository;

    private static final Map<String, String> JOIN_FIELD_MAP = Map.of(
            "workCenterName", "workCenter.centerName"
    );

    @Override
    public MachineDetailsResponseDTO getMachineDetailsById(long id) {

        logger.debug("Fetching MachineDetails for ID: {}", id);

        // Fetch entity
        MachineDetails machineDetails = machineDetailsRepository
                .findByIdAndDeletedDateIsNull(id)
                .orElseThrow(() -> {
                    logger.error("MachineDetails not found for ID: {}", id);
                    return new ResourceNotFoundException("MachineDetails not found for ID: " + id);
                });

        // Map to DTO
        MachineDetailsResponseDTO responseDTO =
                machineDetailsResponseMapper.toDTO(machineDetails);

        // Fetch last status update date
        LocalDateTime lastUpdateDate = machineStatusHistoryRepository
                .findTopByMachineIdOrderByChangedAtDesc(id)
                .map(MachineStatusHistory::getChangedAt)
                .orElse(null);

        responseDTO.setLastUpdate(lastUpdateDate);

        return responseDTO;
    }

    @Override
    public List<MachineDetailsResponseDTO> getMachineList() {
        logger.debug("Fetching all MachineDetails");
        List<MachineDetailsResponseDTO> dtos = machineDetailsRepository.findByDeletedDateIsNull().stream()
                .map(machineDetailsResponseMapper::toDTO)
                .collect(Collectors.toList());
        logger.debug("Retrieved {} active MachineDetails records", dtos.size());
        return dtos;
    }

    @Override
    public MachineDetailsResponseDTO createMachineDetails(MachineDetails machineDetails) {
        logger.debug("Creating new MachineDetails: {}", machineDetails);
        validateMachineDetails(machineDetails);
        if (machineDetailsRepository.findByMachineCodeAndDeletedDateIsNull(machineDetails.getMachineCode()).isPresent()) {
            throw new IllegalStateException("MachineDetails already exists for code: " + machineDetails.getMachineCode());
        }
        MachineDetails savedMachine = machineDetailsRepository.save(machineDetails);
        logger.info("Successfully created MachineDetails with ID: {}", savedMachine.getId());
        return machineDetailsResponseMapper.toDTO(savedMachine);
    }

    @Override
    @Transactional
    public MachineDetailsResponseDTO updateMachineDetails(long id, MachineDetails updatedMachineDetails) {
        logger.info("Attempting to update MachineDetails with ID: {}", id);

        MachineDetails existingMachine = machineDetailsRepository.findByIdAndDeletedDateIsNull(id)
                .orElseThrow(() -> {
                    logger.error("MachineDetails not found for update, ID: {}", id);
                    return new ResourceNotFoundException("MachineDetails not found for ID: " + id);
                });

        validateMachineDetails(updatedMachineDetails);
        machineDetailsRepository.findByMachineCodeAndDeletedDateIsNull(updatedMachineDetails.getMachineCode())
                .ifPresent(machine -> {
                    if (!machine.getId().equals(existingMachine.getId())) {
                        throw new IllegalStateException("MachineDetails already exists for code: " + updatedMachineDetails.getMachineCode());
                    }
                });

        MachineDetails.MachineStatus oldStatus = existingMachine.getMachineStatus();
        MachineDetails.MachineStatus newStatus = updatedMachineDetails.getMachineStatus();

        existingMachine.setMachineCode(updatedMachineDetails.getMachineCode());
        existingMachine.setMachineName(updatedMachineDetails.getMachineName());
        existingMachine.setDescription(updatedMachineDetails.getDescription());
        existingMachine.setWorkCenter(updatedMachineDetails.getWorkCenter());
        existingMachine.setCostPerHour(updatedMachineDetails.getCostPerHour());
        existingMachine.setMachineStatus(newStatus);

        MachineDetails savedMachine = machineDetailsRepository.save(existingMachine);
        if (!Objects.equals(oldStatus, newStatus)) {
            saveStatusHistory(
                    savedMachine,
                    oldStatus,
                    newStatus,
                    "Status changed from MachineDetails update",
                    MachineStatusHistory.Source.MANUAL,
                    LocalDateTime.now()
            );
        }

        return machineDetailsResponseMapper.toDTO(savedMachine);
    }

    @Override
    @Transactional
    public MachineDetailsResponseDTO changeMachineStatus(long id, MachineDetails.MachineStatus newStatus, String reason) {
        return changeMachineStatus(id, newStatus, reason, LocalDateTime.now());
    }

    @Override
    @Transactional
    public MachineDetailsResponseDTO changeMachineStatus(long id,
                                                         MachineDetails.MachineStatus newStatus,
                                                         String reason,
                                                         LocalDateTime changedAt) {
        if (newStatus == null) {
            throw new IllegalArgumentException("New status is required");
        }

        MachineDetails machine = machineDetailsRepository.findByIdAndDeletedDateIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("MachineDetails not found for ID: " + id));

        MachineDetails.MachineStatus oldStatus = machine.getMachineStatus();
        if (!Objects.equals(oldStatus, newStatus)) {
            machine.setMachineStatus(newStatus);
            MachineDetails savedMachine = machineDetailsRepository.save(machine);
            saveStatusHistory(
                    savedMachine,
                    oldStatus,
                    newStatus,
                    StringUtils.hasText(reason) ? reason : "Status changed through changeMachineStatus",
                    MachineStatusHistory.Source.MANUAL,
                    changedAt
            );
            return machineDetailsResponseMapper.toDTO(savedMachine);
        }

        return machineDetailsResponseMapper.toDTO(machine);
    }

    @Override
    @Transactional
    public void deleteMachineDetails(long id) {
        logger.debug("Attempting to soft delete MachineDetails with ID: {}", id);
        MachineDetails machineDetailsToDelete = machineDetailsRepository.findByIdAndDeletedDateIsNull(id)
                .orElseThrow(() -> {
                    logger.error("MachineDetails not found for ID: {}", id);
                    return new ResourceNotFoundException("MachineDetails not found for ID: " + id);
                });
        machineDetailsToDelete.setDeletedDate(new Date());
        machineDetailsRepository.save(machineDetailsToDelete);
        logger.info("Successfully soft deleted MachineDetails with ID: {}", id);
    }

    @Override
    public Page<MachineDetailsResponseDTO> filterMachineDetails(FilterRequest request) {
        Sort.Direction direction = StringUtils.hasText(request.getSortDir())
                ? Sort.Direction.fromString(request.getSortDir())
                : Sort.Direction.ASC;
        String sortBy = StringUtils.hasText(request.getSortBy()) ? request.getSortBy() : "id";
        if (JOIN_FIELD_MAP.containsKey(sortBy)) {
            sortBy = JOIN_FIELD_MAP.get(sortBy);
        }
        Sort sort = Sort.by(direction, sortBy);

        List<FilterCriteria> filters = request.getFilters() != null
                ? new ArrayList<>(request.getFilters())
                : new ArrayList<>();
        String searchTerm = extractSearchTerm(filters);
        FilterCriteria filterDeleteDateIsNull = new FilterCriteria("deletedDate", "=", null);
        filters.add(filterDeleteDateIsNull);
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), sort);

        Specification<MachineDetails> spec = GenericSpecification.buildSpecification(filters, JOIN_FIELD_MAP);
        if (StringUtils.hasText(searchTerm)) {
            spec = spec.and(searchAcrossMachineFields(searchTerm));
        }
        Page<MachineDetails> machineDetails = machineDetailsRepository.findAll(spec, pageable);

        return machineDetails.map(machineDetailsResponseMapper::toDTO);

    }

    private String extractSearchTerm(List<FilterCriteria> filters) {
        String searchTerm = filters.stream()
                .filter(Objects::nonNull)
                .filter(f -> StringUtils.hasText(f.getField()))
                .filter(f -> "search".equalsIgnoreCase(f.getField()) || "query".equalsIgnoreCase(f.getField()))
                .map(FilterCriteria::getValue)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);

        filters.removeIf(f ->
                f != null
                        && StringUtils.hasText(f.getField())
                        && ("search".equalsIgnoreCase(f.getField()) || "query".equalsIgnoreCase(f.getField()))
        );
        return searchTerm;
    }

    private Specification<MachineDetails> searchAcrossMachineFields(String rawSearchTerm) {
        String search = "%" + rawSearchTerm.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.like(cb.lower(root.get("machineCode")), search));
            predicates.add(cb.like(cb.lower(root.get("machineName")), search));
            predicates.add(cb.like(cb.lower(root.get("description")), search));
            predicates.add(cb.like(cb.lower(root.join("workCenter", JoinType.LEFT).get("centerName")), search));
            predicates.add(cb.like(cb.lower(root.get("machineStatus").as(String.class)), search));
            return cb.or(predicates.toArray(new Predicate[0]));
        };
    }

    private void validateMachineDetails(MachineDetails machineDetails) {
        if (!StringUtils.hasText(machineDetails.getMachineCode())) {
            throw new IllegalArgumentException("Machine code is required");
        }
        if (!StringUtils.hasText(machineDetails.getMachineName())) {
            throw new IllegalArgumentException("Machine name is required");
        }
        if (machineDetails.getWorkCenter() == null) {
            throw new IllegalArgumentException("Work center is required");
        }
        if (machineDetails.getCostPerHour() == null || machineDetails.getCostPerHour().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Cost per hour must be zero or positive");
        }
        if (machineDetails.getMachineStatus() == null) {
            throw new IllegalArgumentException("Machine status is required");
        }
    }

    private void saveStatusHistory(
            MachineDetails machine,
            MachineDetails.MachineStatus oldStatus,
            MachineDetails.MachineStatus newStatus,
            String reason,
            MachineStatusHistory.Source source,
            LocalDateTime changedAt
    ) {
        MachineStatusHistory history = new MachineStatusHistory();
        history.setMachine(machine);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setChangedAt(changedAt != null ? changedAt : LocalDateTime.now());
        history.setChangedBy(getCurrentActor());
        history.setReason(reason);
        history.setSource(source);
        machineStatusHistoryRepository.save(history);
    }

    @Override
    public MachineDetails getMachineDetailsEntityById(long id) {
        return machineDetailsRepository.findByIdAndDeletedDateIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("MachineDetails not found for ID: " + id));
    }

    private String getCurrentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !StringUtils.hasText(authentication.getName())) {
            return "SYSTEM";
        }
        return authentication.getName();
    }
}
