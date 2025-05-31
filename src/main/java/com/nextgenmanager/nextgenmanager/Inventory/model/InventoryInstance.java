package com.nextgenmanager.nextgenmanager.Inventory.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.Date;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@DynamicUpdate
@Table(name = "inventoryInstance")
public class InventoryInstance {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(unique = true, nullable = false)
    private String uniqueId = generateShortUUID();

    @ManyToOne
    @JoinColumn(name = "inventoryItemRef", referencedColumnName = "inventoryItemId")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"}) // Prevents serialization issues
    private InventoryItem inventoryItem;

    private double quantity;

    @Column
    private boolean isConsumed=false;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date entryDate;

    @Temporal(TemporalType.TIMESTAMP)
    private Date consumeDate;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "bookedDate")
    private Date bookedDate;

    @Temporal(TemporalType.TIMESTAMP)
    private Date requestedDate;

    @Temporal(TemporalType.TIMESTAMP)
    private Date deliveryDate;


    private Double costPerUnit;

    private Double sellPricePerUnit;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;

    private static String generateShortUUID() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}