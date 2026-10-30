package com.nextgenmanager.nextgenmanager.sales.model;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Table(name = "salesCreditNoteItem")
public class SalesCreditNoteItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_credit_note_id", nullable = false)
    private SalesCreditNote salesCreditNote;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    private int lineNumber;

    private double returnedQty;
    private double rate;
    private double gstRate;
    private double gstAmount;
    private double totalAmount;

    /** Where the goods went. Set to the warehouse actually used once the note is confirmed. */
    private String warehouseTo;
    private String remarks;

    /** Null on lines confirmed before V191, which all restocked as good stock. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SalesReturnGoodsCondition goodsCondition;

    /** The unit cost the returned stock was valued at, so a cancellation reverses the same amount. */
    private Double restockRate;
}
