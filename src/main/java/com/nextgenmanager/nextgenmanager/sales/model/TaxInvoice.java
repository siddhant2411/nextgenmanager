package com.nextgenmanager.nextgenmanager.sales.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "taxInvoice")
public class TaxInvoice {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne
    private SalesOrder salesOrder;

    private String invoiceNo;
    private Date invoiceDate;
    private BigDecimal totalAmount;

    @OneToMany(mappedBy = "taxInvoice", cascade = CascadeType.ALL)
    private List<InvoiceItem> items;
}
