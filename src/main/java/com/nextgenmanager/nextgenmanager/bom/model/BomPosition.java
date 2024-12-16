package com.nextgenmanager.nextgenmanager.bom.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.Date;

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

    // Many BomPositions belong to one BOM (Many-to-One relationship)
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "bomId", nullable = false)
//    private Bom parentBom;

    // Many BomPositions can reference one InventoryItem (Many-to-One relationship)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventoryItemId", nullable = false)
    private InventoryItem childInventoryItem;

    @Column(name = "position")
    private int position;

    private double quantity;

}
