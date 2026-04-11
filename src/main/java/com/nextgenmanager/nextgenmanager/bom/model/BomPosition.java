package com.nextgenmanager.nextgenmanager.bom.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.production.model.RoutingOperation;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "bomPosition")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class BomPosition {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parentBomId", nullable = false)
    private Bom parentBom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "childInventoryItemId", nullable = true)
    private InventoryItem childInventoryItem;

    @Column(name = "position")
    private int position;

    private double quantity;

    private BigDecimal scrapPercentage;

    /**
     * Which routing operation consumes this component.
     * NULL = issue at work-order level (no specific operation gate).
     * Set NULL on delete so rebuilding a routing auto-clears stale references.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routingOperationId")
    private RoutingOperation routingOperation;

}
