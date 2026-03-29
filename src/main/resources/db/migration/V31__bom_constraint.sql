ALTER table bom
drop CONSTRAINT IF EXISTS uq_bom_one_active_per_item;