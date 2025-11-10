package com.nextgenmanager.nextgenmanager.bom.model;

import com.fasterxml.jackson.annotation.*;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItemAttachment;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.Date;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "bom")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@JsonIdentityInfo(
        generator = ObjectIdGenerators.PropertyGenerator.class,
        property = "id"
)
public class Bom {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    @Column(name = "bomName")
    private String bomName;
    // Many BOMs can have one parent InventoryItem (Many-to-One relationship)

    @OneToOne
    @JoinColumn(name = "parentInventoryItemId", nullable = false, unique = true)
//    @JsonBackReference
    private InventoryItem parentInventoryItem;

    @OneToMany
    @JoinColumn(name = "bomPositionId")
    private List<BomPosition> childInventoryItems; // Assuming BomPosition is another entity

    @Enumerated(EnumType.ORDINAL)
    @Column(nullable = false)
    private BomStatus bomStatus;

    @Column(name = "effectiveFrom")
    private Date effectiveFrom;

    @Column(name = "effectiveTo")
    private Date effectiveTo;

    @Column(name = "version")
    private String revision;

    @Column(name = "ecoNumber")
    private String ecoNumber;

    @Column(name = "changeReason")
    private String changeReason;

    @Column(name = "approvedBy")
    private String approvedBy;

    @Column(name = "approvalDate")
    private Date approvalDate;

    @Column(name = "approvalComments")
    private String approvalComments;

    @Column(name = "description")
    private String description;

    @Column(name = "isActive")
    private Boolean isActive = true;

    @Column(name = "isDefault")
    private Boolean isDefault;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;

    @OneToMany(mappedBy = "bom", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<BomAttachment> bomAttachmentList;
}
