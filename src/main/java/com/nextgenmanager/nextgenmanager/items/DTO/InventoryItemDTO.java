package com.nextgenmanager.nextgenmanager.items.DTO;

import com.nextgenmanager.nextgenmanager.items.model.ItemType;
import com.nextgenmanager.nextgenmanager.items.model.UOM;
import lombok.*;


/**This DTO is for display the table on UI
 *
 * @author Siddhant Mavani
 * @version 1.0.0
 * @since 18-10-2025
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class InventoryItemDTO {

    private int inventoryItemId;
    private String itemCode;
    private String name;
    private String hsnCode;
    private UOM uom;
    private ItemType itemType;
    private String dimension;
    private String size;
    private String weight;
    private String basicMaterial;
    private double availableQuantity;
    private byte revision;
    private String drawingNumber;
    private double sellingPrice;
    private boolean manufactured;
    private boolean purchased;
    private double leadTime;
    private Double standardCost;


}
