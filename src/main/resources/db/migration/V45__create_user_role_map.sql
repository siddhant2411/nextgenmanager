CREATE SEQUENCE public.userrolemap_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.userrolemap (
    id bigint NOT NULL,
    userId bigint NOT NULL,
    roleId bigint NOT NULL,
    createdBy character varying(100),
    updatedBy character varying(100),
    creationDate timestamp(6) without time zone,
    updatedDate timestamp(6) without time zone,
    CONSTRAINT userrolemap_pkey PRIMARY KEY (id),
    CONSTRAINT userrolemap_userId_roleId_key UNIQUE (userId, roleId),
    CONSTRAINT fk_userrolemap_userId FOREIGN KEY (userId) REFERENCES public.appuser(id),
    CONSTRAINT fk_userrolemap_roleId FOREIGN KEY (roleId) REFERENCES public.role(id)
);

CREATE INDEX idx_userrolemap_userId ON public.userrolemap (userId);
CREATE INDEX idx_userrolemap_roleId ON public.userrolemap (roleId);

