-- Flat BOM cost lines for CONSUMABLE items.
--
-- A bomCostLine is a flat, unmeasured cost added to a BOM (e.g. "Grease ₹10") for a CONSUMABLE
-- master item where there is no honest per-unit quantity. It is costing-only: never exploded
-- into work-order materials, never issued, never posted to the GL. It references an existing
-- inventory item (created in the Product Master — the BOM never creates items).
--
-- id uses GenerationType.AUTO, so Hibernate drives a per-entity sequence named bomcostline_seq
-- with INCREMENT BY 50 (allocationSize), matching every other entity in this schema.

CREATE SEQUENCE bomCostLine_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE bomCostLine (
    id               INTEGER       NOT NULL PRIMARY KEY,
    parentBomId      INTEGER       NOT NULL,
    inventoryItemId  INTEGER       NOT NULL,
    amount           NUMERIC(14,2),
    position         INTEGER       NOT NULL DEFAULT 0,

    CONSTRAINT fk_bomCostLine_bom  FOREIGN KEY (parentBomId)     REFERENCES bom(id),
    CONSTRAINT fk_bomCostLine_item FOREIGN KEY (inventoryItemId) REFERENCES inventoryItem(inventoryItemId)
);

CREATE INDEX idx_bomCostLine_parentBom ON bomCostLine (parentBomId);
CREATE INDEX idx_bomCostLine_item      ON bomCostLine (inventoryItemId);

-- inventoryItem.itemType and .uom are persisted as enum ORDINALS, but the baseline (V1) check
-- constraints were never widened as the enums grew: ItemType now has 5 values (0..4, incl.
-- SUB_CONTRACTED and CONSUMABLE) and UOM has 9 (0..8). The old constraints (0..2 / 0..3) reject
-- the newer ordinals, so creating e.g. a CONSUMABLE item or an INCH/LITER item fails. Widen both.
ALTER TABLE inventoryItem DROP CONSTRAINT IF EXISTS inventoryitem_itemtype_check;
ALTER TABLE inventoryItem ADD  CONSTRAINT inventoryitem_itemtype_check CHECK (itemtype >= 0 AND itemtype <= 4);

ALTER TABLE inventoryItem DROP CONSTRAINT IF EXISTS inventoryitem_uom_check;
ALTER TABLE inventoryItem ADD  CONSTRAINT inventoryitem_uom_check CHECK (uom >= 0 AND uom <= 8);
