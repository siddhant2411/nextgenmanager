package com.nextgenmanager.nextgenmanager.Inventory.model;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "stocktransferline")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class StockTransferLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stocktransfer_id", nullable = false)
    private StockTransfer stockTransfer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventoryItemRef", referencedColumnName = "inventoryItemId", nullable = false)
    private InventoryItem inventoryItem;

    @Column(precision = 18, scale = 4, nullable = false)
    private BigDecimal quantity;

    /**
     * What actually arrived. A short receipt is legitimate — damage, miscount — and the shortfall
     * stays inTransit at the source rather than evaporating, so it shows up as something to
     * investigate instead of quietly balancing.
     */
    @Column(precision = 18, scale = 4, nullable = false)
    private BigDecimal receivedQuantity = BigDecimal.ZERO;

    @Column(length = 300)
    private String remarks;
}
