-- Phase 3 (perpetual inventory): ledgers for stock adjustments / write-offs.
-- Stock receipt (2010/2011/2012), GR/IR Clearing (6030) and COGS (5020E) already exist from V128;
-- only the adjustment gain/loss accounts are new. Auto-posting routes ADJUSTMENT movements here:
--   stock increase (+qty) → Cr Inventory Adjustment Gain (income)
--   stock decrease (-qty) → Dr Inventory Adjustment / Write-off (expense)

INSERT INTO ledgeraccount (code, name, group_id, nature, isControlAccount, subLedgerType, isBankAccount, isCashAccount, isReconcilable, gstApplicable)
SELECT t.code, t.name, ag.id, t.nature, t.isCtrl, t.slType, t.isBank, t.isCash, t.isRec, t.gstApp
FROM (VALUES
    ('5063E', 'Inventory Adjustment / Write-off', 'DIRECT_EXPENSES',  'EXPENSE', false, 'NONE', false, false, false, false),
    ('4024I', 'Inventory Adjustment Gain',        'OTHER_INCOME_GRP', 'INCOME',  false, 'NONE', false, false, false, false)
) AS t(code, name, grp, nature, isCtrl, slType, isBank, isCash, isRec, gstApp)
JOIN accountgroup ag ON ag.code = t.grp
ON CONFLICT (code) DO NOTHING;

-- Wired into auto-posting — users may rename but not delete.
UPDATE ledgeraccount SET isSystem = true WHERE code IN ('5063E', '4024I');
