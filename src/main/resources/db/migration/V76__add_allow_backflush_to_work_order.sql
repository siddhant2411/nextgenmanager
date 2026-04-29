-- V76: Add allowBackflush column to WorkOrder table
ALTER TABLE workorder ADD COLUMN allowBackflush BOOLEAN NOT NULL DEFAULT FALSE;
