package com.nextgenmanager.nextgenmanager.sales.quotation.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.contact.model.ContactAddress;
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

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "quotation")
public class Quotation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    @Column(unique = true, nullable = false)
    private String qtnNo;

    private LocalDate qtnDate;

    private String enqNo;

    private LocalDate enqDate;

    @ManyToOne(fetch = FetchType.LAZY) // Lazy load the associated Contact
    @JoinColumn(name = "contact_id", referencedColumnName = "id") // Foreign key mapping
    private Contact contact;

    @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<QuotationProducts> quotationProducts;

    private BigDecimal netAmount;

    @Min(0)
    private BigDecimal gstPercentage;

    private BigDecimal  gstAmount;

    private BigDecimal  roundOff;

    private BigDecimal  totalAmount;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;


}
