-- Drop existing status check constraint
ALTER TABLE public.salesorder DROP CONSTRAINT IF EXISTS salesorder_status_check;

-- Add updated status check constraint with new values
ALTER TABLE public.salesorder ADD CONSTRAINT salesorder_status_check 
CHECK (status::text = ANY (ARRAY[
    'DRAFT'::character varying, 
    'APPROVED'::character varying, 
    'IN_PROGRESS'::character varying, 
    'PARTIALLY_DISPATCHED'::character varying, 
    'FULLY_DISPATCHED'::character varying, 
    'INVOICED'::character varying, 
    'COMPLETED'::character varying, 
    'CANCELLED'::character varying
]::text[]));
