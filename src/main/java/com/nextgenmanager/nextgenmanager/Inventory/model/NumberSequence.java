package com.nextgenmanager.nextgenmanager.Inventory.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "numberSequence")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class NumberSequence {

    /**
     * Key format:
     *   BATCH-{ITEMCODE}-{YYYYMM}
     *   SERIAL-{ITEMCODE}-{YYYYMM}
     */
    @Id
    private String sequenceKey;

    @Column(nullable = false)
    private long nextVal = 1L;
}
