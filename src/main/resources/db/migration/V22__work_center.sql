-- 1. Drop existing default on id
ALTER TABLE workcenter_available_shifts RENAME TO workCenterAvailableShifts;

-- 2. Convert to identity column (recommended)
ALTER TABLE workCenterAvailableShifts RENAME COLUMN available_shifts TO availableShifts;
ALTER TABLE workCenterAvailableShifts RENAME COLUMN list_order TO listOrder;