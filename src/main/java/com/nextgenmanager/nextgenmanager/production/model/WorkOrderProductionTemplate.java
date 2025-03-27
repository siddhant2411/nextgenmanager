package com.nextgenmanager.nextgenmanager.production.model;

import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "WorkOrderProductionTemplate")
public class WorkOrderProductionTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    @OneToOne(fetch = FetchType.LAZY) // Lazy load the associated Contact
    @JoinColumn(name = "bom_id", referencedColumnName = "id") // Foreign key mapping
    private Contact contact;



}
