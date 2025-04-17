package com.nextgenmanager.nextgenmanager.items.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "item_code")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ItemCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int year;

    @Column(name = "sequence_number")
    private int sequenceNumber;

    @Column(unique = true)
    private String code;
}
