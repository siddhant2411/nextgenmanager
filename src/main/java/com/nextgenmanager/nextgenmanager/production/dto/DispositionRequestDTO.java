package com.nextgenmanager.nextgenmanager.production.dto;

import com.nextgenmanager.nextgenmanager.production.enums.DispositionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DispositionRequestDTO {

    private Long rejectionEntryId;

    private DispositionStatus dispositionStatus;

    private String dispositionReason;

    /**
     * Quantity to dispose. Null or equal to the full rejection qty disposes the entire entry.
     * A smaller value splits the entry — the disposed qty becomes a new closed entry with
     * the chosen status, and the remainder stays on the original entry as PENDING.
     */
    private BigDecimal quantity;

    /** Required when dispositionStatus == REWORK: which operation sequence to restart from */
    private Integer reworkOperationSequence;
}
