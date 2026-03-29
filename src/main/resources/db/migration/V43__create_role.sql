CREATE SEQUENCE public.role_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.role (
    id bigint NOT NULL,
    roleName character varying(100) NOT NULL,
    displayName character varying(120) NOT NULL,
    roleDescription character varying(500),
    moduleName character varying(100),
    roleType character varying(30) NOT NULL DEFAULT 'SYSTEM',
    isSystemRole boolean NOT NULL DEFAULT false,
    isActive boolean NOT NULL DEFAULT true,
    createdBy character varying(100),
    updatedBy character varying(100),
    creationDate timestamp(6) without time zone,
    updatedDate timestamp(6) without time zone,
    deletedDate timestamp(6) without time zone,
    CONSTRAINT role_pkey PRIMARY KEY (id),
    CONSTRAINT role_roleName_key UNIQUE (roleName),
    CONSTRAINT role_roleType_check CHECK (roleType IN ('SYSTEM', 'MODULE', 'CUSTOM'))
);

CREATE INDEX idx_role_moduleName ON public.role (moduleName);
CREATE INDEX idx_role_isActive ON public.role (isActive);
