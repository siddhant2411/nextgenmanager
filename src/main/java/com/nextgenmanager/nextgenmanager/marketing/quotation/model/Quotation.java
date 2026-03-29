package com.nextgenmanager.nextgenmanager.marketing.quotation.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.Enquiry;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "quotation")
public class Quotation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(unique = true)
    private String qtnNo;

    private LocalDate qtnDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enquiry_id", referencedColumnName = "id")
    private Enquiry enquiry;

    @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<QuotationProducts> quotationProducts;

    @Column(precision = 10, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "packagingAndForwardingCharges", precision = 10, scale = 2)
    private BigDecimal packagingAndForwardingCharges;

    @Min(0)
    @Column(name = "packagingAndForwardingChargesPercentage", precision = 5, scale = 2)
    private BigDecimal packagingAndForwardingChargesPercentage;

    @Min(0)
    @Column(precision = 5, scale = 2)
    private BigDecimal gstPercentage;

    @Min(0)
    @Column(precision = 5, scale = 2)
    private BigDecimal discountPercentage;

    @Column(precision = 10, scale = 2)
    private BigDecimal gstAmount;

    @Column(precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @Column(precision = 10, scale = 2)
    private BigDecimal roundOff;

    @Column(precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    private QuotationStatus quotationStatus = QuotationStatus.DRAFT;

    private String validTill;
    private String paymentTerms;
    private String deliveryTerms;
    private String inspectionTerms;
    private String pricesTerms;
    private String notes;
    private String createdBy;
    private String updatedBy;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;
}
