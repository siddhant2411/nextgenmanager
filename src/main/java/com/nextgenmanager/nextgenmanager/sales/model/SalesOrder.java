package com.nextgenmanager.nextgenmanager.sales.model;


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
@Table(name = "salesOrder")
public class SalesOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;


    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    private String salesOrderNumber;
    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;

}
