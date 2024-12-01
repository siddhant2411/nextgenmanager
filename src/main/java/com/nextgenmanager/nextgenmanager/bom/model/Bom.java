package com.nextgenmanager.nextgenmanager.bom.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
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
public class Bom {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    @Column(name = "bomName")
    private String bomName;
    // Many BOMs can have one parent InventoryItem (Many-to-One relationship)
    @ManyToOne
    @JoinColumn(name = "parentInventoryItemId", nullable = false) // The foreign key column name
    private InventoryItem parentInventoryItem;

    // One BOM can have many child BOMPositions (One-to-Many relationship)
    @OneToMany

    @JoinColumn(name = "bomPositionId")
    private List<BomPosition> childInventoryItems; // Assuming BomPosition is another entity

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;
}
