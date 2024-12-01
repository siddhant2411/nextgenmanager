package com.nextgenmanager.nextgenmanager.bom.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BomDTO {
    private int id;
    private String bomName;
    private String itemCode;
    private String name;
}