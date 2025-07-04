package com.nextgenmanager.nextgenmanager.sales.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryNote {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne
    private SalesOrder salesOrder;

    private String deliveryNoteNo;
    private Date deliveryDate;
    private String lrNumber;
    private String transporter;

    @OneToMany(mappedBy = "deliveryNote", cascade = CascadeType.ALL)
    private List<DeliveryNoteItem> items;
}
