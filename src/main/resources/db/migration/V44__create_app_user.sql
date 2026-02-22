CREATE SEQUENCE public.appuser_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.appuser (
    id bigint NOT NULL,
    username character varying(100) NOT NULL,
    passwordHash character varying(255) NOT NULL,
    email character varying(150),
    isActive boolean NOT NULL DEFAULT true,
    isLocked boolean NOT NULL DEFAULT false,
    lastLoginDate timestamp(6) without time zone,
    createdBy character varying(100),
    updatedBy character varying(100),
    creationDate timestamp(6) without time zone,
    updatedDate timestamp(6) without time zone,
    deletedDate timestamp(6) without time zone,
    CONSTRAINT appuser_pkey PRIMARY KEY (id),
    CONSTRAINT appuser_username_key UNIQUE (username),
    CONSTRAINT appuser_email_key UNIQUE (email)
);

CREATE INDEX idx_appuser_isActive ON public.appuser (isActive);

