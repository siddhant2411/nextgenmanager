ALTER TABLE public.bom
    DROP CONSTRAINT IF EXISTS fkhfk5evomv0y33u3n8uibkadn8,
    ADD CONSTRAINT fk_bom_parentinventoryitem FOREIGN KEY (parentinventoryitemid)
        REFERENCES public.inventoryitem(inventoryitemid);

ALTER TABLE public.bom
    ADD CONSTRAINT uk_bom_parentinventoryitemid UNIQUE (parentinventoryitemid);
