-- V128: Chart of Accounts — accountgroup + ledgeraccount tables + seeded Indian COA.
-- MT note: these tables are per-tenant (no schema prefix). In MT fork mirror as V128_1__.

CREATE TABLE IF NOT EXISTS accountgroup (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(120) UNIQUE NOT NULL,
    name            VARCHAR(120) NOT NULL,
    parentGroup_id  BIGINT REFERENCES accountgroup(id),
    nature          VARCHAR(12) NOT NULL
        CHECK (nature IN ('ASSET','LIABILITY','EQUITY','INCOME','EXPENSE')),
    scheduleIIIHead VARCHAR(120),
    sortOrder       INTEGER NOT NULL DEFAULT 0,
    deletedDate     TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ledgeraccount (
    id                  BIGSERIAL PRIMARY KEY,
    code                VARCHAR(120) UNIQUE NOT NULL,
    name                VARCHAR(150) NOT NULL,
    group_id            BIGINT NOT NULL REFERENCES accountgroup(id),
    nature              VARCHAR(12) NOT NULL
        CHECK (nature IN ('ASSET','LIABILITY','EQUITY','INCOME','EXPENSE')),
    openingBalance      NUMERIC(12,2) NOT NULL DEFAULT 0,
    openingBalanceDrCr  VARCHAR(2) CHECK (openingBalanceDrCr IN ('DR','CR')),
    isControlAccount    BOOLEAN NOT NULL DEFAULT false,
    subLedgerType       VARCHAR(12) NOT NULL DEFAULT 'NONE'
        CHECK (subLedgerType IN ('CUSTOMER','VENDOR','BANK','CASH','TAX','TDS','NONE')),
    contact_id          INTEGER REFERENCES contact(id),
    gstApplicable       BOOLEAN NOT NULL DEFAULT false,
    gstRate             NUMERIC(5,2),
    isBankAccount       BOOLEAN NOT NULL DEFAULT false,
    isCashAccount       BOOLEAN NOT NULL DEFAULT false,
    isReconcilable      BOOLEAN NOT NULL DEFAULT false,
    isSystem            BOOLEAN NOT NULL DEFAULT false,
    creationDate        TIMESTAMP NOT NULL DEFAULT NOW(),
    updatedDate         TIMESTAMP NOT NULL DEFAULT NOW(),
    deletedDate         TIMESTAMP,
    -- An opening balance is meaningless without a Dr/Cr side.
    CONSTRAINT chk_ledger_opening CHECK (openingBalance = 0 OR openingBalanceDrCr IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_ledgeraccount_group    ON ledgeraccount(group_id);
CREATE INDEX IF NOT EXISTS idx_ledgeraccount_contact  ON ledgeraccount(contact_id);
CREATE INDEX IF NOT EXISTS idx_ledgeraccount_subtype  ON ledgeraccount(subLedgerType);

-- ─── Seed Account Groups ────────────────────────────────────────────────────
-- Step 1: top-level groups (no parent)
INSERT INTO accountgroup (code, name, nature, scheduleIIIHead, sortOrder) VALUES
    ('ASSETS',                'Assets',                     'ASSET',     'Balance Sheet - Assets',                  10),
    ('LIABILITIES',           'Liabilities',                'LIABILITY', 'Balance Sheet - Liabilities',              20),
    ('EQUITY',                'Equity',                     'EQUITY',    'Balance Sheet - Equity',                   30),
    ('INCOME',                'Income',                     'INCOME',    'Statement of P&L - Income',                40),
    ('EXPENSES',              'Expenses',                   'EXPENSE',   'Statement of P&L - Expenses',              50)
ON CONFLICT (code) DO NOTHING;

-- Step 2: Level-2 groups
INSERT INTO accountgroup (code, name, nature, scheduleIIIHead, sortOrder) VALUES
    ('FIXED_ASSETS',          'Fixed Assets',               'ASSET',     'Non-Current Assets',                      11),
    ('CURRENT_ASSETS',        'Current Assets',             'ASSET',     'Current Assets',                          12),
    ('NON_CURRENT_LIAB',      'Non-Current Liabilities',   'LIABILITY', 'Non-Current Liabilities',                  21),
    ('CURRENT_LIAB',          'Current Liabilities',        'LIABILITY', 'Current Liabilities',                      22),
    ('SHARE_CAPITAL_GRP',     'Share Capital',              'EQUITY',    'Equity - Share Capital',                   31),
    ('RESERVES_SURPLUS',      'Reserves & Surplus',         'EQUITY',    'Equity - Reserves & Surplus',              32),
    ('REVENUE',               'Revenue from Operations',    'INCOME',    'P&L - Revenue from Operations',            41),
    ('OTHER_INCOME_GRP',      'Other Income',               'INCOME',    'P&L - Other Income',                       42),
    ('PURCHASE_ACCOUNTS',     'Purchase Accounts',          'EXPENSE',   'P&L - Cost of Materials',                  51),
    ('DIRECT_EXPENSES',       'Direct Expenses',            'EXPENSE',   'P&L - Direct Expenses',                    52),
    ('EMPLOYEE_COSTS',        'Employee Benefit Expense',   'EXPENSE',   'P&L - Employee Costs',                     53),
    ('ADMIN_GENERAL',         'Administrative Expenses',    'EXPENSE',   'P&L - Other Expenses',                     54),
    ('SELLING_EXPENSES',      'Selling & Distribution',     'EXPENSE',   'P&L - Selling Expenses',                   55),
    ('FINANCIAL_CHARGES',     'Finance Costs',              'EXPENSE',   'P&L - Finance Costs',                      56),
    ('DEPRECIATION_GRP',      'Depreciation & Amortisation','EXPENSE',   'P&L - Depreciation',                       57)
ON CONFLICT (code) DO NOTHING;

-- Step 3: Level-3 groups
INSERT INTO accountgroup (code, name, nature, scheduleIIIHead, sortOrder) VALUES
    ('INVENTORIES_GRP',       'Inventories',                'ASSET',     'Current Assets - Inventories',             13),
    ('TRADE_RECEIVABLES_GRP', 'Trade Receivables',          'ASSET',     'Current Assets - Trade Receivables',       14),
    ('CASH_AND_BANK_GRP',     'Cash & Cash Equivalents',    'ASSET',     'Current Assets - Cash & Bank',             15),
    ('LOANS_ADVANCES_ASSET',  'Short-term Loans & Advances','ASSET',     'Current Assets - Loans & Advances',        16),
    ('OTHER_CURRENT_ASSETS',  'Other Current Assets',       'ASSET',     'Current Assets - Other',                   17),
    ('LONG_TERM_BORROWINGS',  'Long-term Borrowings',       'LIABILITY', 'Non-Current Liabilities - Borrowings',     23),
    ('TRADE_PAYABLES_GRP',    'Trade Payables',             'LIABILITY', 'Current Liabilities - Trade Payables',     24),
    ('DUTIES_AND_TAXES',      'Duties & Taxes',             'LIABILITY', 'Current Liabilities - Duties & Taxes',     25),
    ('OTHER_CURRENT_LIAB',    'Other Current Liabilities',  'LIABILITY', 'Current Liabilities - Other',              26)
ON CONFLICT (code) DO NOTHING;

-- Step 4: Set parent relationships
UPDATE accountgroup g SET parentGroup_id = p.id FROM accountgroup p WHERE g.code = 'FIXED_ASSETS'          AND p.code = 'ASSETS';
UPDATE accountgroup g SET parentGroup_id = p.id FROM accountgroup p WHERE g.code = 'CURRENT_ASSETS'        AND p.code = 'ASSETS';
UPDATE accountgroup g SET parentGroup_id = p.id FROM accountgroup p WHERE g.code = 'NON_CURRENT_LIAB'      AND p.code = 'LIABILITIES';
UPDATE accountgroup g SET parentGroup_id = p.id FROM accountgroup p WHERE g.code = 'CURRENT_LIAB'          AND p.code = 'LIABILITIES';
UPDATE accountgroup g SET parentGroup_id = p.id FROM accountgroup p WHERE g.code = 'SHARE_CAPITAL_GRP'     AND p.code = 'EQUITY';
UPDATE accountgroup g SET parentGroup_id = p.id FROM accountgroup p WHERE g.code = 'RESERVES_SURPLUS'      AND p.code = 'EQUITY';
UPDATE accountgroup g SET parentGroup_id = p.id FROM accountgroup p WHERE g.code = 'REVENUE'               AND p.code = 'INCOME';
UPDATE accountgroup g SET parentGroup_id = p.id FROM accountgroup p WHERE g.code = 'OTHER_INCOME_GRP'      AND p.code = 'INCOME';
UPDATE accountgroup g SET parentGroup_id = p.id FROM accountgroup p WHERE g.code = 'PURCHASE_ACCOUNTS'     AND p.code = 'EXPENSES';
UPDATE accountgroup g SET parentGroup_id = p.id FROM accountgroup p WHERE g.code = 'DIRECT_EXPENSES'       AND p.code = 'EXPENSES';
UPDATE accountgroup g SET parentGroup_id = p.id FROM accountgroup p WHERE g.code = 'EMPLOYEE_COSTS'        AND p.code = 'EXPENSES';
UPDATE accountgroup g SET parentGroup_id = p.id FROM accountgroup p WHERE g.code = 'ADMIN_GENERAL'         AND p.code = 'EXPENSES';
UPDATE accountgroup g SET parentGroup_id = p.id FROM accountgroup p WHERE g.code = 'SELLING_EXPENSES'      AND p.code = 'EXPENSES';
UPDATE accountgroup g SET parentGroup_id = p.id FROM accountgroup p WHERE g.code = 'FINANCIAL_CHARGES'     AND p.code = 'EXPENSES';
UPDATE accountgroup g SET parentGroup_id = p.id FROM accountgroup p WHERE g.code = 'DEPRECIATION_GRP'      AND p.code = 'EXPENSES';
UPDATE accountgroup g SET parentGroup_id = p.id FROM accountgroup p WHERE g.code = 'INVENTORIES_GRP'       AND p.code = 'CURRENT_ASSETS';
UPDATE accountgroup g SET parentGroup_id = p.id FROM accountgroup p WHERE g.code = 'TRADE_RECEIVABLES_GRP' AND p.code = 'CURRENT_ASSETS';
UPDATE accountgroup g SET parentGroup_id = p.id FROM accountgroup p WHERE g.code = 'CASH_AND_BANK_GRP'     AND p.code = 'CURRENT_ASSETS';
UPDATE accountgroup g SET parentGroup_id = p.id FROM accountgroup p WHERE g.code = 'LOANS_ADVANCES_ASSET'  AND p.code = 'CURRENT_ASSETS';
UPDATE accountgroup g SET parentGroup_id = p.id FROM accountgroup p WHERE g.code = 'OTHER_CURRENT_ASSETS'  AND p.code = 'CURRENT_ASSETS';
UPDATE accountgroup g SET parentGroup_id = p.id FROM accountgroup p WHERE g.code = 'LONG_TERM_BORROWINGS'  AND p.code = 'NON_CURRENT_LIAB';
UPDATE accountgroup g SET parentGroup_id = p.id FROM accountgroup p WHERE g.code = 'TRADE_PAYABLES_GRP'    AND p.code = 'CURRENT_LIAB';
UPDATE accountgroup g SET parentGroup_id = p.id FROM accountgroup p WHERE g.code = 'DUTIES_AND_TAXES'      AND p.code = 'CURRENT_LIAB';
UPDATE accountgroup g SET parentGroup_id = p.id FROM accountgroup p WHERE g.code = 'OTHER_CURRENT_LIAB'    AND p.code = 'CURRENT_LIAB';

-- ─── Seed Ledger Accounts ───────────────────────────────────────────────────
INSERT INTO ledgeraccount (code, name, group_id, nature, isControlAccount, subLedgerType, isBankAccount, isCashAccount, isReconcilable, gstApplicable)
SELECT t.code, t.name, ag.id, t.nature, t.isCtrl, t.slType, t.isBank, t.isCash, t.isRec, t.gstApp
FROM (VALUES
    -- Fixed Assets
    ('1010', 'Plant & Machinery',          'FIXED_ASSETS',          'ASSET',     false, 'NONE', false, false, false, false),
    ('1011', 'Furniture & Fixtures',       'FIXED_ASSETS',          'ASSET',     false, 'NONE', false, false, false, false),
    ('1012', 'Vehicles',                   'FIXED_ASSETS',          'ASSET',     false, 'NONE', false, false, false, false),
    ('1013', 'Computer Equipment',         'FIXED_ASSETS',          'ASSET',     false, 'NONE', false, false, false, false),
    ('1020', 'Accumulated Depreciation',   'FIXED_ASSETS',          'ASSET',     false, 'NONE', false, false, false, false),
    -- Inventories
    ('2010', 'Raw Material Stock',         'INVENTORIES_GRP',       'ASSET',     false, 'NONE', false, false, false, false),
    ('2011', 'Work-in-Progress Stock',     'INVENTORIES_GRP',       'ASSET',     false, 'NONE', false, false, false, false),
    ('2012', 'Finished Goods Stock',       'INVENTORIES_GRP',       'ASSET',     false, 'NONE', false, false, false, false),
    ('2013', 'Packing Material Stock',     'INVENTORIES_GRP',       'ASSET',     false, 'NONE', false, false, false, false),
    -- Trade Receivables
    ('3010', 'Sundry Debtors',             'TRADE_RECEIVABLES_GRP', 'ASSET',     true,  'CUSTOMER', false, false, false, false),
    -- Cash & Bank
    ('4010', 'Cash in Hand',               'CASH_AND_BANK_GRP',     'ASSET',     false, 'CASH', false, true,  false, false),
    ('4011', 'Bank Account - Primary',     'CASH_AND_BANK_GRP',     'ASSET',     false, 'BANK', true,  false, true,  false),
    -- Loans & Advances (Asset)
    ('5010', 'Advance to Suppliers',       'LOANS_ADVANCES_ASSET',  'ASSET',     false, 'NONE', false, false, false, false),
    ('5011', 'Prepaid Expenses',           'LOANS_ADVANCES_ASSET',  'ASSET',     false, 'NONE', false, false, false, false),
    -- Other Current Assets
    ('6010', 'TDS Receivable',             'OTHER_CURRENT_ASSETS',  'ASSET',     false, 'TDS',  false, false, false, false),
    ('6011', 'Advance Tax Paid',           'OTHER_CURRENT_ASSETS',  'ASSET',     false, 'NONE', false, false, false, false),
    ('6020', 'Input CGST',                 'OTHER_CURRENT_ASSETS',  'ASSET',     false, 'TAX',  false, false, false, true),
    ('6021', 'Input SGST',                 'OTHER_CURRENT_ASSETS',  'ASSET',     false, 'TAX',  false, false, false, true),
    ('6022', 'Input IGST',                 'OTHER_CURRENT_ASSETS',  'ASSET',     false, 'TAX',  false, false, false, true),
    ('6023', 'Input Cess',                 'OTHER_CURRENT_ASSETS',  'ASSET',     false, 'TAX',  false, false, false, true),
    ('6024', 'GST Paid under RCM',         'OTHER_CURRENT_ASSETS',  'ASSET',     false, 'TAX',  false, false, false, true),
    ('6030', 'GR/IR Clearing',             'OTHER_CURRENT_ASSETS',  'ASSET',     false, 'NONE', false, false, false, false),
    -- Long-term Borrowings
    ('7010', 'Secured Loans',              'LONG_TERM_BORROWINGS',  'LIABILITY', false, 'NONE', false, false, false, false),
    ('7011', 'Unsecured Loans',            'LONG_TERM_BORROWINGS',  'LIABILITY', false, 'NONE', false, false, false, false),
    -- Trade Payables
    ('8010', 'Sundry Creditors',           'TRADE_PAYABLES_GRP',    'LIABILITY', true,  'VENDOR', false, false, false, false),
    -- Duties & Taxes
    ('9010', 'Output CGST',               'DUTIES_AND_TAXES',       'LIABILITY', false, 'TAX',  false, false, false, true),
    ('9011', 'Output SGST',               'DUTIES_AND_TAXES',       'LIABILITY', false, 'TAX',  false, false, false, true),
    ('9012', 'Output IGST',               'DUTIES_AND_TAXES',       'LIABILITY', false, 'TAX',  false, false, false, true),
    ('9013', 'Output Cess',               'DUTIES_AND_TAXES',       'LIABILITY', false, 'TAX',  false, false, false, true),
    ('9014', 'RCM Payable',               'DUTIES_AND_TAXES',       'LIABILITY', false, 'TAX',  false, false, false, true),
    ('9015', 'TDS Payable',               'DUTIES_AND_TAXES',       'LIABILITY', false, 'TDS',  false, false, false, false),
    ('9016', 'GST Payable (Net)',          'DUTIES_AND_TAXES',       'LIABILITY', false, 'TAX',  false, false, false, false),
    ('9017', 'PF Payable',                'DUTIES_AND_TAXES',       'LIABILITY', false, 'NONE', false, false, false, false),
    ('9018', 'ESI Payable',               'DUTIES_AND_TAXES',       'LIABILITY', false, 'NONE', false, false, false, false),
    ('9019', 'Professional Tax Payable',  'DUTIES_AND_TAXES',       'LIABILITY', false, 'NONE', false, false, false, false),
    -- Other Current Liabilities
    ('9030', 'Advance from Customers',    'OTHER_CURRENT_LIAB',     'LIABILITY', false, 'NONE', false, false, false, false),
    ('9031', 'Salary Payable',            'OTHER_CURRENT_LIAB',     'LIABILITY', false, 'NONE', false, false, false, false),
    ('9032', 'Audit Fees Payable',        'OTHER_CURRENT_LIAB',     'LIABILITY', false, 'NONE', false, false, false, false),
    -- Equity
    ('3010E','Share Capital',             'SHARE_CAPITAL_GRP',      'EQUITY',    false, 'NONE', false, false, false, false),
    ('3020E','Retained Earnings',         'RESERVES_SURPLUS',       'EQUITY',    false, 'NONE', false, false, false, false),
    ('3021E','General Reserve',           'RESERVES_SURPLUS',       'EQUITY',    false, 'NONE', false, false, false, false),
    -- Revenue
    ('4010I','Sales - Domestic',          'REVENUE',                'INCOME',    false, 'NONE', false, false, false, false),
    ('4011I','Sales - Export',            'REVENUE',                'INCOME',    false, 'NONE', false, false, false, false),
    -- Other Income
    ('4020I','Interest Received',         'OTHER_INCOME_GRP',       'INCOME',    false, 'NONE', false, false, false, false),
    ('4021I','Discount Received',         'OTHER_INCOME_GRP',       'INCOME',    false, 'NONE', false, false, false, false),
    ('4022I','Miscellaneous Income',      'OTHER_INCOME_GRP',       'INCOME',    false, 'NONE', false, false, false, false),
    ('4023I','Round-off Income',          'OTHER_INCOME_GRP',       'INCOME',    false, 'NONE', false, false, false, false),
    -- Purchases
    ('5010E','Purchases - Raw Material',  'PURCHASE_ACCOUNTS',      'EXPENSE',   false, 'NONE', false, false, false, false),
    ('5011E','Purchases - Trading Goods', 'PURCHASE_ACCOUNTS',      'EXPENSE',   false, 'NONE', false, false, false, false),
    -- Direct Expenses
    ('5020E','COGS / Cost of Production', 'DIRECT_EXPENSES',        'EXPENSE',   false, 'NONE', false, false, false, false),
    ('5021E','Freight Inward',            'DIRECT_EXPENSES',        'EXPENSE',   false, 'NONE', false, false, false, false),
    ('5022E','Job-Work Charges',          'DIRECT_EXPENSES',        'EXPENSE',   false, 'NONE', false, false, false, true),
    ('5023E','Packing Material Used',     'DIRECT_EXPENSES',        'EXPENSE',   false, 'NONE', false, false, false, false),
    ('5024E','Power & Fuel',              'DIRECT_EXPENSES',        'EXPENSE',   false, 'NONE', false, false, false, false),
    -- Employee Costs
    ('5030E','Salaries & Wages',          'EMPLOYEE_COSTS',         'EXPENSE',   false, 'NONE', false, false, false, false),
    ('5031E','Labour Charges',            'EMPLOYEE_COSTS',         'EXPENSE',   false, 'NONE', false, false, false, false),
    ('5032E','Director Remuneration',     'EMPLOYEE_COSTS',         'EXPENSE',   false, 'NONE', false, false, false, false),
    -- Admin & General
    ('5040E','Rent',                      'ADMIN_GENERAL',          'EXPENSE',   false, 'NONE', false, false, false, false),
    ('5041E','Printing & Stationery',     'ADMIN_GENERAL',          'EXPENSE',   false, 'NONE', false, false, false, false),
    ('5042E','Telephone & Internet',      'ADMIN_GENERAL',          'EXPENSE',   false, 'NONE', false, false, false, false),
    ('5043E','Office Expenses',           'ADMIN_GENERAL',          'EXPENSE',   false, 'NONE', false, false, false, false),
    ('5044E','Repairs & Maintenance',     'ADMIN_GENERAL',          'EXPENSE',   false, 'NONE', false, false, false, false),
    -- Selling
    ('5050E','Freight Outward',           'SELLING_EXPENSES',       'EXPENSE',   false, 'NONE', false, false, false, false),
    ('5051E','Advertisement',             'SELLING_EXPENSES',       'EXPENSE',   false, 'NONE', false, false, false, false),
    ('5052E','Discount Allowed',          'SELLING_EXPENSES',       'EXPENSE',   false, 'NONE', false, false, false, false),
    -- Financial
    ('5060E','Bank Charges',              'FINANCIAL_CHARGES',      'EXPENSE',   false, 'NONE', false, false, false, false),
    ('5061E','Interest Paid',             'FINANCIAL_CHARGES',      'EXPENSE',   false, 'NONE', false, false, false, false),
    ('5062E','Round-off Expense',         'FINANCIAL_CHARGES',      'EXPENSE',   false, 'NONE', false, false, false, false),
    -- Depreciation
    ('5070E','Depreciation',              'DEPRECIATION_GRP',       'EXPENSE',   false, 'NONE', false, false, false, false)
) AS t(code, name, grp, nature, isCtrl, slType, isBank, isCash, isRec, gstApp)
JOIN accountgroup ag ON ag.code = t.grp
ON CONFLICT (code) DO NOTHING;

-- ─── Protect structural accounts from deletion ──────────────────────────────
-- These ledgers are wired into auto-posting (control accounts, GST, cash/bank,
-- inventory, round-off, retained earnings). Users may rename but not delete them.
UPDATE ledgeraccount SET isSystem = true WHERE code IN (
    '3010',  '8010',                                  -- control: Sundry Debtors / Creditors
    '4010',  '4011',                                  -- Cash in Hand / Bank Account
    '2010',  '2011', '2012', '2013',                  -- inventory stock (perpetual)
    '6020',  '6021', '6022', '6023', '6024', '6030',  -- Input GST / RCM / GR-IR Clearing
    '9010',  '9011', '9012', '9013', '9014', '9015', '9016', -- Output GST / RCM / TDS / GST Payable
    '5020E', '4023I', '5062E',                         -- COGS, Round-off Income / Expense
    '3020E'                                            -- Retained Earnings (year-end close)
);
