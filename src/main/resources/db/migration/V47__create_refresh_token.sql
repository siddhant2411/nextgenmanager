CREATE SEQUENCE public.refreshtoken_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.refreshtoken (
    id bigint NOT NULL,
    userId bigint NOT NULL,
    token character varying(1200) NOT NULL,
    expiryDate timestamp(6) without time zone NOT NULL,
    revoked boolean NOT NULL DEFAULT false,
    revokedDate timestamp(6) without time zone,
    createdBy character varying(100),
    updatedBy character varying(100),
    creationDate timestamp(6) without time zone,
    updatedDate timestamp(6) without time zone,
    deletedDate timestamp(6) without time zone,
    CONSTRAINT refreshtoken_pkey PRIMARY KEY (id),
    CONSTRAINT refreshtoken_token_key UNIQUE (token),
    CONSTRAINT fk_refreshtoken_userId FOREIGN KEY (userId) REFERENCES public.appuser(id)
);

CREATE INDEX idx_refreshtoken_userId ON public.refreshtoken (userId);
CREATE INDEX idx_refreshtoken_revoked ON public.refreshtoken (revoked);
