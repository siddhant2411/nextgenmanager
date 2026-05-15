-- V114: Add only missing GST supply details to salesorder
-- Items and other tax amounts already exist in the baseline schema

ALTER TABLE salesorder ADD COLUMN IF NOT EXISTS placeofsupply VARCHAR(100);
ALTER TABLE salesorder ADD COLUMN IF NOT EXISTS placeofsupplystatecode VARCHAR(5);
