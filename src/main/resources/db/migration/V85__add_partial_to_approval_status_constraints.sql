-- Add PARTIAL to approval status check constraints on inventoryRequest and inventoryBookingApproval

ALTER TABLE inventoryrequest
    DROP CONSTRAINT inventoryrequest_approvalstatus_check,
    ADD CONSTRAINT inventoryrequest_approvalstatus_check
        CHECK (approvalstatus IN ('PENDING', 'APPROVED', 'PARTIAL', 'REJECTED'));

ALTER TABLE inventorybookingapproval
    DROP CONSTRAINT inventorybookingapproval_approvalstatus_check,
    ADD CONSTRAINT inventorybookingapproval_approvalstatus_check
        CHECK (approvalstatus IN ('PENDING', 'APPROVED', 'PARTIAL', 'REJECTED'));
