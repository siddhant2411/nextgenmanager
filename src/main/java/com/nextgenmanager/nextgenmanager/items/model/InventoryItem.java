package com.nextgenmanager.nextgenmanager.items.model;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.common.model.FileAttachment;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.web.multipart.MultipartFile;

import java.util.Date;
import java.util.List;
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "inventoryItem")
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int inventoryItemId;

    @Column(unique = true, nullable = false)
    private String itemCode;

    @Column(nullable = false)
    private String name;

    private String hsnCode;

    @Column(nullable = false)
    private UOM uom;

    @Column(nullable = false)
    private ItemType itemType;

    private byte revision;

    private String remarks;

    @Column(nullable = true)
    private String itemGroupCode;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;


    @OneToOne(mappedBy = "inventoryItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private ProductSpecification productSpecification;

    @OneToOne(mappedBy = "inventoryItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private ProductInventorySettings productInventorySettings;

    @OneToOne(mappedBy = "inventoryItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private ProductFinanceSettings productFinanceSettings;


    @Transient
    @JsonIgnore
    private List<MultipartFile> attachments;

    @Transient
    private List<FileAttachment> fileAttachments;

    /**
     * Optional: ID of an ItemCodeSeries to use for auto item-code generation on create.
     * Not persisted — consumed by InventoryItemServiceImpl to generate itemCode.
     */
    @Transient
    private Long seriesId;
}