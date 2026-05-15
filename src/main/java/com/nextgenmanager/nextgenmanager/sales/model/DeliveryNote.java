package com.nextgenmanager.nextgenmanager.sales.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
    @JsonIgnoreProperties({"deliveryNotes", "taxInvoices", "items"})
    private SalesOrder salesOrder;

    private String deliveryNoteNo;
    private Date deliveryDate;
    private String lrNumber;
    private String transporter;
    private String vehicleNumber;
    private String ewayBillNumber;
    private String dispatchThrough;
    private String remarks;

    @OneToMany(mappedBy = "deliveryNote", cascade = CascadeType.ALL)
    private List<DeliveryNoteItem> items;
}
