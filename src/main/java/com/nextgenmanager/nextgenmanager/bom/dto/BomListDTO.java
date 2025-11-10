package com.nextgenmanager.nextgenmanager.bom.dto;

import com.nextgenmanager.nextgenmanager.bom.model.BomStatus;
import lombok.*;

import java.util.Date;

/**This DTO is for display the table on UI
 *
 * @author Siddhant Mavani
 * @version 1.0.0
 * @since 08-11-2025
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class BomListDTO {

    private int id;
    private String bomName;
    private String parentItemCode;
    private String parentItemName;
    private String revision;
    private BomStatus bomStatus;
    private String parentDrawingNumber;
    private Date effectiveFrom;
    private Date effectiveTo;

}
