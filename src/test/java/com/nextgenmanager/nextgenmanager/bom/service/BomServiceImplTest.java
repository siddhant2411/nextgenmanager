package com.nextgenmanager.nextgenmanager.bom.service;

import com.nextgenmanager.nextgenmanager.bom.dto.BomCostBreakdownDTO;
import com.nextgenmanager.nextgenmanager.bom.mapper.BomListMapper;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.model.BomCostLine;
import com.nextgenmanager.nextgenmanager.bom.model.BomPosition;
import com.nextgenmanager.nextgenmanager.bom.model.BomStatus;
import com.nextgenmanager.nextgenmanager.bom.repository.BomRepository;
import com.nextgenmanager.nextgenmanager.bom.repository.routing.RoutingRepository;
import com.nextgenmanager.nextgenmanager.common.events.DomainEventPublisher;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ProductFinanceSettings;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.items.service.InventoryItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BomServiceImplTest {

    @Mock private BomRepository bomRepository;
    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private InventoryItemService inventoryItemService;
    @Mock private RoutingRepository routingRepository;
    @Mock private DomainEventPublisher domainEventPublisher;
    @Mock private BomListMapper bomListMapper;

    @InjectMocks
    private BomServiceImpl service;

    // @InjectMocks uses the 2-arg constructor and then skips field injection, so wire the
    // @Autowired collaborators onto the service explicitly.
    @BeforeEach
    void wireFields() {
        ReflectionTestUtils.setField(service, "bomRepository", bomRepository);
        ReflectionTestUtils.setField(service, "inventoryItemRepository", inventoryItemRepository);
        ReflectionTestUtils.setField(service, "inventoryItemService", inventoryItemService);
        ReflectionTestUtils.setField(service, "routingRepository", routingRepository);
    }

    // ── Cost breakdown rollup ───────────────────────────────────────────────

    @Test
    void getBomCostBreakdown_includesAdditionalCostsInTotal() {
        InventoryItem parent = item(1, "FG-1", "Gear", null);
        InventoryItem steel = item(2, "RM-1", "Steel", 100.0);
        InventoryItem grease = item(3, "CONS-1", "Grease", 8.0);

        Bom bom = new Bom();
        bom.setId(50);
        bom.setBomName("Gear BOM");
        bom.setParentInventoryItem(parent);
        bom.setPositions(new ArrayList<>(List.of(position(steel, 2.0))));       // 2 × 100 = 200 material
        bom.setCostLines(new ArrayList<>(List.of(costLine(grease, new BigDecimal("10.00"))))); // flat ₹10

        when(bomRepository.findById(50)).thenReturn(Optional.of(bom));
        when(inventoryItemService.getInventoryItem(2)).thenReturn(steel);
        when(routingRepository.findByBomId(50)).thenReturn(Optional.empty());

        BomCostBreakdownDTO breakdown = service.getBomCostBreakdown(50);

        assertThat(breakdown.getTotalMaterialCost()).isEqualByComparingTo("200.00");
        assertThat(breakdown.getAdditionalCosts()).hasSize(1);
        assertThat(breakdown.getAdditionalCosts().get(0).getItemName()).isEqualTo("Grease");
        // Uses the per-BOM price (₹10), not the item's standardCost (₹8)
        assertThat(breakdown.getTotalAdditionalCost()).isEqualByComparingTo("10.00");
        assertThat(breakdown.getTotalCost()).isEqualByComparingTo("210.00"); // 200 + 0 ops + 10
    }

    // ── Cost line references an existing master item (never creates one) ─────

    @Test
    void addBom_persistsCostLineReferencingExistingItem_withoutCreatingAnyItem() {
        InventoryItem parent = item(9, "FG-9", "Assembly", null);
        InventoryItem grease = item(3, "CONS-3", "Grease", 8.0);

        Bom bom = draftBom(parent);
        bom.setCostLines(new ArrayList<>(List.of(costLineRef(3, new BigDecimal("10")))));

        when(inventoryItemRepository.findById(9)).thenReturn(Optional.of(parent));
        when(inventoryItemRepository.findById(3)).thenReturn(Optional.of(grease));
        when(bomRepository.save(any(Bom.class))).thenAnswer(inv -> inv.getArgument(0));

        service.addBom(bom);

        verify(inventoryItemRepository, never()).save(any(InventoryItem.class));
        assertThat(bom.getCostLines().get(0).getInventoryItem()).isSameAs(grease);
    }

    @Test
    void addBom_throwsWhenCostLineHasNoItemReference() {
        InventoryItem parent = item(9, "FG-9", "Assembly", null);

        Bom bom = draftBom(parent);
        BomCostLine orphan = new BomCostLine();
        orphan.setAmount(new BigDecimal("10")); // no inventory item
        bom.setCostLines(new ArrayList<>(List.of(orphan)));

        when(inventoryItemRepository.findById(9)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> service.addBom(bom))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("existing inventory item");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static InventoryItem item(int id, String code, String name, Double standardCost) {
        InventoryItem item = new InventoryItem();
        item.setInventoryItemId(id);
        item.setItemCode(code);
        item.setName(name);
        if (standardCost != null) {
            ProductFinanceSettings fin = new ProductFinanceSettings();
            fin.setStandardCost(standardCost);
            item.setProductFinanceSettings(fin);
        }
        return item;
    }

    private static BomPosition position(InventoryItem child, double qty) {
        BomPosition pos = new BomPosition();
        pos.setChildInventoryItem(child);
        pos.setQuantity(qty);
        pos.setScrapPercentage(BigDecimal.ZERO);
        return pos;
    }

    /** Cost line already holding the resolved item (breakdown path). */
    private static BomCostLine costLine(InventoryItem item, BigDecimal amount) {
        BomCostLine line = new BomCostLine();
        line.setInventoryItem(item);
        line.setAmount(amount);
        return line;
    }

    /** Inbound cost line referencing an item by id only (service resolves it). */
    private static BomCostLine costLineRef(int itemId, BigDecimal amount) {
        BomCostLine line = new BomCostLine();
        InventoryItem stub = new InventoryItem();
        stub.setInventoryItemId(itemId);
        line.setInventoryItem(stub);
        line.setAmount(amount);
        return line;
    }

    private static Bom draftBom(InventoryItem parent) {
        Bom bom = new Bom();
        bom.setBomName("Test BOM");
        bom.setParentInventoryItem(parent);
        bom.setPositions(new ArrayList<>());
        bom.setBomStatus(BomStatus.DRAFT);
        return bom;
    }
}
