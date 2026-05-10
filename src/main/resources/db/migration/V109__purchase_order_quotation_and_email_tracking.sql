-- Vendor quotation reference on purchase orders
ALTER TABLE purchaseorder
    ADD COLUMN IF NOT EXISTS quotationNumber VARCHAR(100),
    ADD COLUMN IF NOT EXISTS quotationDate   TIMESTAMP;

-- Email-sent tracking
ALTER TABLE purchaseorder
    ADD COLUMN IF NOT EXISTS sentToVendorAt    TIMESTAMP,
    ADD COLUMN IF NOT EXISTS sentToVendorEmail VARCHAR(255);

-- Message templates for Purchase Order communication
INSERT INTO message_template (name, body, subject, channel, category, is_default, created_by, creation_date, updated_date)
VALUES
(
  'PO Email - Standard',
  E'Dear {{contactPerson}},\n\nPlease find attached our Purchase Order **{{poNumber}}** raised against your quotation **{{quotationNumber}}** dated {{quotationDate}}.\n\nOrder Details:\n- PO Number   : {{poNumber}}\n- Order Date  : {{orderDate}}\n- Delivery By : {{expectedDeliveryDate}}\n- Total Value : {{currency}} {{grandTotal}}\n\nKindly confirm receipt of this order and revert with an Order Acknowledgement at the earliest.\n\nFor any queries, feel free to reach out.\n\nThanks & Regards,\n{{companyName}}',
  'Purchase Order {{poNumber}} – Against Quotation {{quotationNumber}}',
  'EMAIL',
  'PURCHASE_ORDER',
  TRUE,
  'system',
  NOW(),
  NOW()
),
(
  'PO WhatsApp - Standard',
  E'Hi {{contactPerson}},\n\nWe have raised a Purchase Order *{{poNumber}}* against your quotation *{{quotationNumber}}*.\n\nOrder Date: {{orderDate}}\nDelivery By: {{expectedDeliveryDate}}\nValue: {{currency}} {{grandTotal}}\n\nThe PO document is shared separately. Request your acknowledgement.\n\nRegards,\n{{companyName}}',
  NULL,
  'WHATSAPP',
  'PURCHASE_ORDER',
  TRUE,
  'system',
  NOW(),
  NOW()
),
(
  'PO Email - Urgent',
  E'Dear {{contactPerson}},\n\nThis is an URGENT purchase order request.\n\nWe are sending Purchase Order **{{poNumber}}** against your quotation **{{quotationNumber}}**. Delivery is required by **{{expectedDeliveryDate}}** and cannot be delayed.\n\nTotal Order Value: {{currency}} {{grandTotal}}\n\nPlease acknowledge and confirm delivery timeline immediately.\n\nThanks & Regards,\n{{companyName}}',
  'URGENT – Purchase Order {{poNumber}} | Delivery Required by {{expectedDeliveryDate}}',
  'EMAIL',
  'PURCHASE_ORDER',
  FALSE,
  'system',
  NOW(),
  NOW()
);
