-- Phase 3 migration support: counter-account for opening stock loaded into a live perpetual system.
-- When a company onboards opening stock per item (ProcurementDecision.OPENING_STOCK), the inventory
-- funnel writes an InventoryLedger row (transactionType ADJUSTMENT, referenceType OPENING_STOCK).
-- Accounting posts it Dr Raw Material/Finished Goods Stock  /  Cr Opening Balance Equity — equity,
-- NOT Inventory Adjustment Gain (income). Opening Balance Equity is the temporary cutover bridge;
-- once the full opening trial balance is entered it nets to the firm's capital.

INSERT INTO ledgeraccount (code, name, group_id, nature, isControlAccount, subLedgerType, isBankAccount, isCashAccount, isReconcilable, gstApplicable)
SELECT t.code, t.name, ag.id, t.nature, t.isCtrl, t.slType, t.isBank, t.isCash, t.isRec, t.gstApp
FROM (VALUES
    ('3022E', 'Opening Balance Equity', 'RESERVES_SURPLUS', 'EQUITY', false, 'NONE', false, false, false, false)
) AS t(code, name, grp, nature, isCtrl, slType, isBank, isCash, isRec, gstApp)
JOIN accountgroup ag ON ag.code = t.grp
ON CONFLICT (code) DO NOTHING;

-- Wired into auto-posting (opening-stock cutover) — users may rename but not delete.
UPDATE ledgeraccount SET isSystem = true WHERE code = '3022E';
