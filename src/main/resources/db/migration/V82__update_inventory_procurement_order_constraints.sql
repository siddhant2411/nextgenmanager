-- Drop the old constraint
ALTER TABLE inventoryprocurementorder
DROP CONSTRAINT IF EXISTS inventoryprocurementorder_procurementdecision_check;

-- Add the new constraint with all current enum values
ALTER TABLE inventoryprocurementorder
ADD CONSTRAINT inventoryprocurementorder_procurementdecision_check 
CHECK (procurementdecision = ANY (ARRAY['UNDECIDED', 'WORK_ORDER', 'PURCHASE_ORDER', 'OPENING_STOCK', 'MANUAL_ENTRY', 'ADJUSTMENT']));
