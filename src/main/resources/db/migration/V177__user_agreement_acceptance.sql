-- User agreement acceptance.
--
-- Every user must accept the current user agreement before using the application. Acceptance is
-- recorded per agreement VERSION, not as a flag on appuser: when the agreement text changes, the
-- version in app.agreement.version is bumped and everyone is asked again, while the rows for older
-- versions stay behind as the record of what each user agreed to and when.
--
-- ipAddress and userAgent are kept because a click-through acceptance is only as good as the
-- evidence that a particular person made it.

CREATE SEQUENCE public.useragreementacceptance_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.useragreementacceptance (
    id bigint NOT NULL,
    userId bigint NOT NULL,
    agreementVersion character varying(50) NOT NULL,
    acceptedDate timestamp(6) without time zone NOT NULL,
    ipAddress character varying(64),
    userAgent character varying(500),
    CONSTRAINT useragreementacceptance_pkey PRIMARY KEY (id),
    CONSTRAINT uq_useragreementacceptance_user_version UNIQUE (userId, agreementVersion),
    CONSTRAINT fk_useragreementacceptance_userId FOREIGN KEY (userId) REFERENCES public.appuser(id)
);

CREATE INDEX idx_useragreementacceptance_userId ON public.useragreementacceptance (userId);
