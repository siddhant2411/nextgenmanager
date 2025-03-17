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
    private int id;

    @Column(unique = true, nullable = false)
    private String qtnNo=generateShortUUID();

    private LocalDate qtnDate;

    @ManyToOne(fetch = FetchType.LAZY) // Lazy load the associated Contact
    @JoinColumn(name = "enquiry_id", referencedColumnName = "id") // Foreign key mapping
    private Enquiry enquiry;


    @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<QuotationProducts> quotationProducts;

    private BigDecimal netAmount;

    private BigDecimal pandfcharges;


    @Min(0)
    private BigDecimal gstPercentage;


    @Min(0)
    private BigDecimal discountPercentage;

    private BigDecimal  gstAmount;


    private BigDecimal  discountAmount;




    private BigDecimal  roundOff;

    private BigDecimal  totalAmount;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;


    @PrePersist
    public void prePersist() {
        if (this.qtnNo == null) {  // Only set if not already assigned
            this.qtnNo = generateShortUUID();
        }
    }

    private static String generateShortUUID() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }


}
