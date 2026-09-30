package com.nextgenmanager.nextgenmanager.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryNoteCreateDto {
    private Long salesOrderId;

    /**
     * The confirmed pick this dispatch is shipping. When set, the lines and the units consumed
     * come from the pick and {@link #items} is ignored — the picker already decided what left the
     * shelf, and a second opinion at dispatch time is how two documents end up claiming the same
     * stock.
     */
    private Long pickListId;

    /**
     * Ship without a pick. Counter sales and sample dispatches genuinely have no picking step, so
     * the inline path stays — but it has to be asked for by name once a pick is waiting, rather
     * than being what happens when nobody says otherwise.
     */
    private boolean directDispatch;

    private String deliveryNoteNo;
    private Date deliveryDate;
    private String lrNumber;
    private String transporter;
    private String vehicleNumber;
    private String ewayBillNumber;
    private String dispatchThrough;
    private String remarks;
    private List<DeliveryNoteItemDto> items;
}
