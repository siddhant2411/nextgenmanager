package com.nextgenmanager.nextgenmanager.items.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "inventoryItemAttachment")
public class InventoryItemAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "inventory_item_id", referencedColumnName = "inventoryItemId", nullable = false)
//    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
//    @JsonBackReference
//    private InventoryItem inventoryItem;

    private String fileName;
    private String fileType;
    private String filePath; // Store file path instead of file data

    @Temporal(TemporalType.TIMESTAMP)
    private Date uploadedDate = new Date();
}