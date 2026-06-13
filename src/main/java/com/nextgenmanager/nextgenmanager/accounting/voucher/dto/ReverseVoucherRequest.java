package com.nextgenmanager.nextgenmanager.accounting.voucher.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReverseVoucherRequest {
    @NotBlank private String reason;
}
