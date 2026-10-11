package com.nextgenmanager.nextgenmanager.production.service.workcenter;

import com.nextgenmanager.nextgenmanager.Inventory.model.Warehouse;
import com.nextgenmanager.nextgenmanager.Inventory.repository.WarehouseRepository;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.production.dto.WorkCenterResponseDTO;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderStatus;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderRepository;
import com.nextgenmanager.nextgenmanager.production.mapper.WorkCenterResponseMapper;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenter;
import com.nextgenmanager.nextgenmanager.production.repository.workcenter.WorkCenterRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class WorkCenterServiceImpl implements WorkCenterService {

    @Autowired
    private WorkCenterRepository workCenterRepository;

    @Autowired
    private WorkCenterResponseMapper workCenterResponseMapper;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private WorkOrderRepository workOrderRepository;

    /** Statuses in which a work order can have material reserved, issued or part-consumed. */
    private static final List<WorkOrderStatus> HOLDING_STOCK = List.of(
            WorkOrderStatus.SCHEDULED, WorkOrderStatus.MATERIAL_PENDING,
            WorkOrderStatus.READY_FOR_PRODUCTION, WorkOrderStatus.PARTIALLY_READY,
            WorkOrderStatus.RELEASED, WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.MATERIAL_REORDER,
            WorkOrderStatus.READY_FOR_INSPECTION, WorkOrderStatus.TESTING,
            WorkOrderStatus.INSPECTION_FAILED, WorkOrderStatus.HOLD);

    @Override
    public WorkCenterResponseDTO createWorkCenter(WorkCenter workCenter) {
        if (workCenterRepository.existsByCenterCode(workCenter.getCenterCode())) {
            throw new ResourceNotFoundException("Work Center code already exists.");
        }
        workCenter.setWarehouse(resolveWarehouse(workCenter));
        return workCenterResponseMapper.toDTO(workCenterRepository.save(workCenter));

    }

    /**
     * The request names a warehouse by id only. Load the real row so a retired or made-up id is
     * refused here rather than surfacing later as a foreign-key failure.
     */
    private Warehouse resolveWarehouse(WorkCenter incoming) {
        Warehouse requested = incoming.getWarehouse();
        if (requested == null || requested.getId() == null) return null;
        return warehouseRepository.findLiveById(requested.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse does not exist"));
    }

    @Override
    public WorkCenterResponseDTO updateWorkCenter(int id, WorkCenter updatedCenter) {
        WorkCenter existingCenter = getActiveWorkCenterById(id);

        if (!Objects.equals(existingCenter.getCenterCode(), updatedCenter.getCenterCode())
                && existsByCenterCode(updatedCenter.getCenterCode())) {
            throw new ResourceNotFoundException("Work Center code already exists.");
        }

        existingCenter.setCenterCode(updatedCenter.getCenterCode());
        existingCenter.setCenterName(updatedCenter.getCenterName());
        existingCenter.setDescription(updatedCenter.getDescription());
        existingCenter.setMachineCostPerHour(updatedCenter.getMachineCostPerHour());
        existingCenter.setOverheadPercentage(updatedCenter.getOverheadPercentage());
        existingCenter.setAvailableHoursPerDay(updatedCenter.getAvailableHoursPerDay());
        existingCenter.setWorkCenterStatus(updatedCenter.getWorkCenterStatus());
        existingCenter.setDepartment(updatedCenter.getDepartment());
        existingCenter.setLocation(updatedCenter.getLocation());
        Warehouse newStore = resolveWarehouse(updatedCenter);
        requireNoOpenWorkOrdersIfStoreChanges(existingCenter, newStore);
        existingCenter.setWarehouse(newStore);
        existingCenter.setMaxLoadPercentage(updatedCenter.getMaxLoadPercentage());
        existingCenter.setSupervisor(updatedCenter.getSupervisor());
        existingCenter.setAvailableShifts(updatedCenter.getAvailableShifts());

        if (updatedCenter.getWorkStations() != null) {
            updatedCenter.getWorkStations().forEach(machine -> machine.setWorkCenter(existingCenter));
            existingCenter.setWorkStations(updatedCenter.getWorkStations());
        }

        return workCenterResponseMapper.toDTO(workCenterRepository.save(existingCenter));
    }

    /**
     * A work order reserves its material in its work centre's store and consumes it from the same
     * place, by asking the work centre each time. Pointing the centre at another store while an
     * order is open would have it consume from a store that never reserved anything for it, and
     * leave the reservation stranded in the old one.
     */
    private void requireNoOpenWorkOrdersIfStoreChanges(WorkCenter center, Warehouse newStore) {
        Long oldId = center.getWarehouse() != null ? center.getWarehouse().getId() : null;
        Long newId = newStore != null ? newStore.getId() : null;
        if (Objects.equals(oldId, newId)) return;

        List<WorkOrder> open = workOrderRepository.findUsingWorkCenter(center.getId(), HOLDING_STOCK);
        if (open.isEmpty()) return;

        String numbers = open.stream().limit(5).map(WorkOrder::getWorkOrderNumber)
                .collect(Collectors.joining(", "));
        throw new IllegalStateException(String.format(
                "The store of %s cannot be changed while %d work order%s open on it (%s%s). "
                        + "Complete or cancel %s first.",
                center.getCenterCode(), open.size(), open.size() == 1 ? " is" : "s are",
                numbers, open.size() > 5 ? ", ..." : "", open.size() == 1 ? "it" : "them"));
    }

    private WorkCenter getActiveWorkCenterById(int id) {
        WorkCenter workCenter = workCenterRepository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Work Center does not exist"));

        if (workCenter.getDeletedDate() != null) {
            throw new ResourceNotFoundException("Work Center does not exist");
        }

        return workCenter;
    }

    @Override
    public void deleteWorkCenter(int id) {

        WorkCenter workCenter = getActiveWorkCenterById(id);

        workCenter.setDeletedDate(new Date());
        workCenterRepository.save(workCenter);

    }

    @Override
    public WorkCenterResponseDTO getWorkCenterById(int id) {
        return workCenterResponseMapper.toDTO(getActiveWorkCenterById(id));

    }

    @Override
    public WorkCenter getWorkCenterEntityById(int id) {
        return getActiveWorkCenterById(id);

    }


    @Override
    public WorkCenterResponseDTO getWorkCenterByCode(String centerCode) {
        WorkCenter workCenter = workCenterRepository.findByCenterCode(centerCode)
                .orElseThrow(() -> new ResourceNotFoundException("Work Center does not exist"));

        if (workCenter.getDeletedDate() != null) {
            throw new ResourceNotFoundException("Work Center does not exist");
        }

        return workCenterResponseMapper.toDTO(workCenter);
    }

    @Override
    public List<WorkCenter> getAllWorkCenters() {
        return workCenterRepository.findAll()
                .stream()
                .filter(workCenter -> workCenter.getDeletedDate() == null)
                .collect(Collectors.toList());
    }

    @Override
    public List<WorkCenter> getAllActiveWorkCenters() {
        return workCenterRepository.findAllActive();
    }

    @Override
    public List<WorkCenter> searchByNameOrCode(String query) {
        return workCenterRepository.search(query)
                .stream()
                .filter(workCenter -> workCenter.getDeletedDate() == null)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsByCenterCode(String centerCode) {
        return workCenterRepository.findByCenterCode(centerCode)
                .map(workCenter -> workCenter.getDeletedDate() == null)
                .orElse(false);
    }

    @Override
    public WorkCenter updateStatus(int id, WorkCenter.WorkCenterStatus status) {
        WorkCenter workCenter = getActiveWorkCenterById(id);
        workCenter.setWorkCenterStatus(status);
        return workCenterRepository.save(workCenter);
    }

    @Override
    public List<WorkCenter> getCentersByStatus(WorkCenter.WorkCenterStatus status) {
        return workCenterRepository.findByWorkCenterStatus(status)
                .stream()
                .filter(workCenter -> workCenter.getDeletedDate() == null)
                .collect(Collectors.toList());
    }

    @Override
    public Page<WorkCenterResponseDTO> getPaginatedCenters(int page, int size, String sortBy, String sortDir, String search) {
        Pageable pageable = PageRequest.of(page, size,
                sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending());

        Specification<WorkCenter> spec = (root, query, cb) -> {
            Predicate predicate = cb.isNull(root.get("deletedDate"));
            if (search != null && !search.isEmpty()) {
                Predicate centerName = cb.like(cb.lower(root.get("centerName")), "%" + search.toLowerCase() + "%");
                Predicate centerCode = cb.like(cb.lower(root.get("centerCode")), "%" + search.toLowerCase() + "%");
                Predicate textMatch = cb.or(centerName, centerCode);
                predicate = cb.and(predicate, textMatch);
            }
            return predicate;
        };

        Page<WorkCenter> workCenters = workCenterRepository.findAll(spec, pageable);
        Page<WorkCenterResponseDTO> workCenterResponseDTOS = workCenters.map(workCenter -> workCenterResponseMapper.toDTO(workCenter));
        return workCenterResponseDTOS;
    }
}
