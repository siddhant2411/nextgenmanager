package com.nextgenmanager.nextgenmanager.accounting.gst.gstr3b.dto;

import java.math.BigDecimal;

/** Section 3.2: inter-state supplies to unregistered/composition/UIN holders, per place of supply. */
public record Section32Row(
        String stateCode,
        String stateName,
        BigDecimal taxableValue,
        BigDecimal igst
) {}
