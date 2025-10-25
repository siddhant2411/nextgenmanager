--
-- PostgreSQL database dump
--

-- Dumped from database version 17.5 (Debian 17.5-1.pgdg120+1)
-- Dumped by pg_dump version 17.5 (Debian 17.5-1.pgdg120+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
--SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: bom; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.bom (
    id integer NOT NULL,
    bomname character varying(255),
    creationdate timestamp(6) without time zone,
    deleteddate timestamp(6) without time zone,
    updateddate timestamp(6) without time zone,
    parentinventoryitemid integer NOT NULL
);


ALTER TABLE public.bom OWNER TO postgres;

--
-- Name: bom_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.bom_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.bom_seq OWNER TO postgres;

--
-- Name: bomattachment; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.bomattachment (
    id bigint NOT NULL,
    filename character varying(255),
    filepath character varying(255),
    filetype character varying(255),
    uploadeddate timestamp(6) without time zone,
    bom_id integer NOT NULL
);


ALTER TABLE public.bomattachment OWNER TO postgres;

--
-- Name: bomattachment_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.bomattachment_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.bomattachment_seq OWNER TO postgres;

--
-- Name: bomposition; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.bomposition (
    id integer NOT NULL,
    "position" integer,
    quantity double precision NOT NULL,
    inventoryitemid integer NOT NULL,
    bompositionid integer
);


ALTER TABLE public.bomposition OWNER TO postgres;

--
-- Name: bomposition_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.bomposition_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.bomposition_seq OWNER TO postgres;

--
-- Name: contact; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.contact (
    id integer NOT NULL,
    companyname character varying(255),
    creationdate timestamp(6) without time zone,
    deleteddate timestamp(6) without time zone,
    gstnumber character varying(255),
    notes character varying(255),
    updateddate timestamp(6) without time zone
);


ALTER TABLE public.contact OWNER TO postgres;

--
-- Name: contact_address; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.contact_address (
    id integer NOT NULL,
    country character varying(255),
    state character varying(255),
    street1 character varying(255),
    street2 character varying(255),
    contact_id integer,
    pincode character varying(255)
);


ALTER TABLE public.contact_address OWNER TO postgres;

--
-- Name: contact_address_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.contact_address_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.contact_address_seq OWNER TO postgres;

--
-- Name: contact_person_detail; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.contact_person_detail (
    id integer NOT NULL,
    emailid character varying(255),
    personname character varying(255),
    phonenumber character varying(255),
    contact_id integer
);


ALTER TABLE public.contact_person_detail OWNER TO postgres;

--
-- Name: contact_person_detail_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.contact_person_detail_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.contact_person_detail_seq OWNER TO postgres;

--
-- Name: contact_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.contact_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.contact_seq OWNER TO postgres;

--
-- Name: deliverynote; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.deliverynote (
    id bigint NOT NULL,
    deliverydate timestamp(6) without time zone,
    deliverynoteno character varying(255),
    lrnumber character varying(255),
    transporter character varying(255),
    salesorder_id bigint
);


ALTER TABLE public.deliverynote OWNER TO postgres;

--
-- Name: deliverynote_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.deliverynote_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.deliverynote_seq OWNER TO postgres;

--
-- Name: deliverynoteitem; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.deliverynoteitem (
    id bigint NOT NULL,
    quantitydelivered integer NOT NULL,
    deliverynote_id bigint,
    inventoryitem_inventoryitemid integer
);


ALTER TABLE public.deliverynoteitem OWNER TO postgres;

--
-- Name: deliverynoteitem_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.deliverynoteitem_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.deliverynoteitem_seq OWNER TO postgres;

--
-- Name: employee_role; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.employee_role (
    id integer NOT NULL,
    roledescription character varying(255),
    rolename character varying(255) NOT NULL,
    employee_id integer
);


ALTER TABLE public.employee_role OWNER TO postgres;

--
-- Name: employee_role_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.employee_role_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.employee_role_seq OWNER TO postgres;

--
-- Name: employeedetails; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.employeedetails (
    id integer NOT NULL,
    employeetype smallint NOT NULL,
    CONSTRAINT employeedetails_employeetype_check CHECK (((employeetype >= 0) AND (employeetype <= 1)))
);


ALTER TABLE public.employeedetails OWNER TO postgres;

--
-- Name: employeedetails_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.employeedetails_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.employeedetails_seq OWNER TO postgres;

--
-- Name: enquiredproducts; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.enquiredproducts (
    id integer NOT NULL,
    priceperunit double precision,
    productnamerequired character varying(255),
    qty double precision NOT NULL,
    specialinstruction character varying(255),
    enquiry_id integer,
    inventoryItemId integer
);


ALTER TABLE public.enquiredproducts OWNER TO postgres;

--
-- Name: enquiredproducts_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.enquiredproducts_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.enquiredproducts_seq OWNER TO postgres;

--
-- Name: enquiry; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.enquiry (
    id integer NOT NULL,
    closereason character varying(255),
    closeddate date,
    contactpersonemail character varying(255),
    contactpersonname character varying(255),
    contactpersonphone character varying(255),
    createdby character varying(255),
    creationdate timestamp(6) without time zone,
    daysfornextfollowup integer NOT NULL,
    deleteddate timestamp(6) without time zone,
    enqdate date,
    enqno character varying(255) NOT NULL,
    enquirysource character varying(255),
    followupremarks character varying(255),
    lastcontacteddate date,
    nextfollowupdate date,
    opportunityname character varying(255),
    referencenumber character varying(255),
    status character varying(255),
    updatedby character varying(255),
    updateddate timestamp(6) without time zone,
    contact_id integer,
    CONSTRAINT enquiry_status_check CHECK (((status)::text = ANY ((ARRAY['NEW'::character varying, 'CONTACTED'::character varying, 'FOLLOW_UP'::character varying, 'CONVERTED'::character varying, 'CLOSED'::character varying, 'LOST'::character varying])::text[])))
);


ALTER TABLE public.enquiry OWNER TO postgres;

--
-- Name: enquiry_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.enquiry_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.enquiry_seq OWNER TO postgres;

--
-- Name: enquiryconversationrecord; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.enquiryconversationrecord (
    id integer NOT NULL,
    conversation character varying(255),
    creationdate timestamp(6) without time zone,
    deleteddate timestamp(6) without time zone,
    updateddate timestamp(6) without time zone,
    enquiry_conversation_id integer
);


ALTER TABLE public.enquiryconversationrecord OWNER TO postgres;

--
-- Name: enquiryconversationrecord_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.enquiryconversationrecord_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.enquiryconversationrecord_seq OWNER TO postgres;



--
-- Name: inventorybookingapproval; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.inventorybookingapproval (
    id bigint NOT NULL,
    approvaldate timestamp(6) without time zone,
    approvalremarks character varying(255),
    approvalstatus character varying(255),
    approvedby character varying(255),
    instancerequestid bigint,
    CONSTRAINT inventorybookingapproval_approvalstatus_check CHECK (((approvalstatus)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])))
);


ALTER TABLE public.inventorybookingapproval OWNER TO postgres;

--
-- Name: inventorybookingapproval_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.inventorybookingapproval_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.inventorybookingapproval_seq OWNER TO postgres;

--
-- Name: inventoryinstance; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.inventoryinstance (
    id bigint NOT NULL,
    bookeddate timestamp(6) without time zone,
    consumedate timestamp(6) without time zone,
    costperunit double precision,
    creationdate timestamp(6) without time zone,
    deleteddate timestamp(6) without time zone,
    deliverydate timestamp(6) without time zone,
    entrydate timestamp(6) without time zone NOT NULL,
    inventoryinstancestatus character varying(255),
    isconsumed boolean,
    quantity double precision NOT NULL,
    sellpriceperunit double precision,
    uniqueid character varying(255) NOT NULL,
    updateddate timestamp(6) without time zone,
    inventoryitemref integer,
    inventoryrequestid bigint,
    work_order_id integer,
    inventory_ledger_id bigint,
    delivery_note_item_id bigint,
    sales_order_item_id bigint,
    purchase_order_item_id bigint,
    procurementorderid bigint,
    CONSTRAINT inventoryinstance_inventoryinstancestatus_check CHECK (((inventoryinstancestatus)::text = ANY ((ARRAY['PENDING'::character varying, 'REQUESTED'::character varying, 'AVAILABLE'::character varying, 'BOOKED'::character varying, 'CONSUMED'::character varying, 'DESTROYED'::character varying])::text[])))
);


ALTER TABLE public.inventoryinstance OWNER TO postgres;

--
-- Name: inventoryinstance_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.inventoryinstance_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.inventoryinstance_seq OWNER TO postgres;

--
-- Name: inventoryitem; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.inventoryitem (
    inventoryitemid integer NOT NULL,
    availablequantity double precision NOT NULL,
    basicmaterial character varying(255),
    creationdate timestamp(6) without time zone,
    deleteddate timestamp(6) without time zone,
    dimension character varying(255),
    hsncode character varying(255),
    isbatchtracked boolean NOT NULL,
    isserialtracked boolean NOT NULL,
    itemcode character varying(255) NOT NULL,
    itemgroupcode character varying(255),
    itemtype smallint NOT NULL,
    leadtime character varying(255),
    manufactured boolean NOT NULL,
    maxstock character varying(255),
    minstock character varying(255),
    name character varying(255) NOT NULL,
    orderedquantity double precision NOT NULL,
    processtype character varying(255),
    purchased boolean NOT NULL,
    remarks character varying(255),
    reorderlevel character varying(255),
    revision smallint NOT NULL,
    sellingprice double precision,
    size character varying(255),
    standardcost double precision,
    taxcategory character varying(255),
    uom smallint NOT NULL,
    updateddate timestamp(6) without time zone,
    weight character varying(255),
    drawingNumber  VARCHAR(100),
    CONSTRAINT inventoryitem_itemtype_check CHECK (((itemtype >= 0) AND (itemtype <= 2))),
    CONSTRAINT inventoryitem_uom_check CHECK (((uom >= 0) AND (uom <= 3)))
);


ALTER TABLE public.inventoryitem OWNER TO postgres;

--
-- Name: inventoryitem_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.inventoryitem_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.inventoryitem_seq OWNER TO postgres;

--
-- Name: inventoryitemattachment; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.inventoryitemattachment (
    id bigint NOT NULL,
    filename character varying(255),
    filepath character varying(255),
    filetype character varying(255),
    uploadeddate timestamp(6) without time zone,
    inventoryItemId integer NOT NULL
);


ALTER TABLE public.inventoryitemattachment OWNER TO postgres;

--
-- Name: inventoryitemattachment_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.inventoryitemattachment_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.inventoryitemattachment_seq OWNER TO postgres;

--
-- Name: inventoryledger; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.inventoryledger (
    id bigint NOT NULL,
    closingbalance integer NOT NULL,
    movementdate date,
    quantity integer NOT NULL,
    referencedocno character varying(255),
    transactiontype character varying(255),
    inventoryitem_inventoryitemid integer
);


ALTER TABLE public.inventoryledger OWNER TO postgres;

--
-- Name: inventoryledger_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.inventoryledger_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.inventoryledger_seq OWNER TO postgres;

--
-- Name: inventoryprocurementorder; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.inventoryprocurementorder (
    id bigint NOT NULL,
    createdby character varying(255),
    creationdate timestamp(6) without time zone,
    inventoryprocurementstatus character varying(255),
    orderid bigint,
    procurementdecision character varying(255),
    inventoryitemprocurementrequest integer,
    procurementrequestid bigint,
    CONSTRAINT inventoryprocurementorder_inventoryprocurementstatus_check CHECK (((inventoryprocurementstatus)::text = ANY ((ARRAY['CREATED'::character varying, 'IN_PROGRESS'::character varying, 'COMPLETED'::character varying, 'CANCELED'::character varying])::text[]))),
    CONSTRAINT inventoryprocurementorder_procurementdecision_check CHECK (((procurementdecision)::text = ANY ((ARRAY['UNDECIDED'::character varying, 'WORK_ORDER'::character varying, 'PURCHASE_ORDER'::character varying])::text[])))
);


ALTER TABLE public.inventoryprocurementorder OWNER TO postgres;

--
-- Name: inventoryprocurementorder_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.inventoryprocurementorder_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.inventoryprocurementorder_seq OWNER TO postgres;

--
-- Name: inventoryrequest; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.inventoryrequest (
    id bigint NOT NULL,
    approvalstatus character varying(255),
    referencenumber character varying(255),
    requestremarks character varying(255),
    requestsource character varying(255),
    requestedby character varying(255),
    requesteddate timestamp(6) without time zone,
    sourceid bigint,
    inventoryitemrequest integer,
    CONSTRAINT inventoryrequest_approvalstatus_check CHECK (((approvalstatus)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[]))),
    CONSTRAINT inventoryrequest_requestsource_check CHECK (((requestsource)::text = ANY ((ARRAY['WORK_ORDER'::character varying, 'SALES_ORDER'::character varying, 'MANUAL'::character varying])::text[])))
);


ALTER TABLE public.inventoryrequest OWNER TO postgres;

--
-- Name: inventoryrequest_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.inventoryrequest_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.inventoryrequest_seq OWNER TO postgres;

--
-- Name: invoiceitem; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.invoiceitem (
    id bigint NOT NULL,
    quantity integer NOT NULL,
    inventoryitem_inventoryitemid integer,
    taxinvoice_id bigint
);


ALTER TABLE public.invoiceitem OWNER TO postgres;

--
-- Name: invoiceitem_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.invoiceitem_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.invoiceitem_seq OWNER TO postgres;

--
-- Name: item_code; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.item_code (
    id bigint NOT NULL,
    code character varying(255),
    sequence_number integer,
    year integer NOT NULL
);


ALTER TABLE public.item_code OWNER TO postgres;

--
-- Name: item_code_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.item_code ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.item_code_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: itemcodemapping; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.itemcodemapping (
    id bigint NOT NULL,
    category character varying(255) NOT NULL,
    code character varying(255) NOT NULL,
    keyword character varying(255) NOT NULL
);


ALTER TABLE public.itemcodemapping OWNER TO postgres;

--
-- Name: itemcodemapping_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.itemcodemapping ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.itemcodemapping_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: machinedetails; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.machinedetails (
    id integer NOT NULL,
    creationdate timestamp(6) without time zone,
    deleteddate timestamp(6) without time zone,
    description character varying(255),
    machinename character varying(255),
    updateddate timestamp(6) without time zone
);


ALTER TABLE public.machinedetails OWNER TO postgres;

--
-- Name: machinedetails_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.machinedetails_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.machinedetails_seq OWNER TO postgres;

--
-- Name: productionjob; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.productionjob (
    id integer NOT NULL,
    costperhour numeric(10,2),
    creationdate timestamp(6) without time zone,
    deleteddate timestamp(6) without time zone,
    description character varying(500),
    isdeleted boolean NOT NULL,
    jobname character varying(100) NOT NULL,
    rolerequired character varying(50) NOT NULL,
    updateddate timestamp(6) without time zone,
    machine_details_id integer,
    CONSTRAINT productionjob_rolerequired_check CHECK (((rolerequired)::text = ANY ((ARRAY['OPERATOR'::character varying, 'SUPERVISOR'::character varying, 'MAINTENANCE'::character varying, 'QUALITY_CONTROL'::character varying])::text[])))
);


ALTER TABLE public.productionjob OWNER TO postgres;

--
-- Name: productionjob_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.productionjob_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.productionjob_seq OWNER TO postgres;

--
-- Name: purchaseorder; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.purchaseorder (
    id bigint NOT NULL,
    createddate timestamp(6) without time zone,
    deleteddate timestamp(6) without time zone,
    expecteddeliverydate timestamp(6) without time zone,
    orderdate timestamp(6) without time zone,
    purchaseordernumber character varying(255),
    remarks character varying(255),
    status character varying(255),
    updateddate timestamp(6) without time zone,
    salesorder_id bigint,
    vendor_id integer,
    CONSTRAINT purchaseorder_status_check CHECK (((status)::text = ANY ((ARRAY['CREATED'::character varying, 'SENT'::character varying, 'PARTIALLY_RECEIVED'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying])::text[])))
);


ALTER TABLE public.purchaseorder OWNER TO postgres;

--
-- Name: purchaseorder_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.purchaseorder ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.purchaseorder_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: purchaseorderitem; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.purchaseorderitem (
    id bigint NOT NULL,
    quantityordered double precision NOT NULL,
    quantityreceived double precision NOT NULL,
    remarks character varying(255),
    unitprice numeric(38,2),
    item_inventoryitemid integer,
    purchaseorder_id bigint
);


ALTER TABLE public.purchaseorderitem OWNER TO postgres;

--
-- Name: purchaseorderitem_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.purchaseorderitem ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.purchaseorderitem_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: quotation; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.quotation (
    id integer NOT NULL,
    createdby character varying(255),
    creationdate timestamp(6) without time zone,
    deleteddate timestamp(6) without time zone,
    deliveryterms character varying(255),
    discountamount numeric(10,2),
    discountpercentage numeric(5,2),
    gstamount numeric(10,2),
    gstpercentage numeric(5,2),
    inspectionterms character varying(255),
    netamount numeric(10,2),
    notes character varying(255),
    pandfcharges numeric(10,2),
    pandfchargespercentage numeric(5,2),
    paymentterms character varying(255),
    pricesterms character varying(255),
    qtndate date,
    qtnno character varying(255),
    quotationstatus character varying(255),
    roundoff numeric(10,2),
    totalamount numeric(12,2),
    updatedby character varying(255),
    updateddate timestamp(6) without time zone,
    validtill character varying(255),
    enquiry_id integer,
    CONSTRAINT quotation_quotationstatus_check CHECK (((quotationstatus)::text = ANY ((ARRAY['DRAFT'::character varying, 'SENT'::character varying, 'ACCEPTED'::character varying, 'REJECTED'::character varying])::text[])))
);


ALTER TABLE public.quotation OWNER TO postgres;

--
-- Name: quotation_products; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.quotation_products (
    id integer NOT NULL,
    discountpercentage numeric(5,2),
    priceperunit numeric(10,2),
    productnamerequired character varying(255),
    qty numeric(10,2),
    specialinstruction character varying(255),
    totalamountofproduct numeric(12,2),
    unitpriceafterdiscount numeric(10,2),
    inventoryItemId integer,
    quotation_id integer
);


ALTER TABLE public.quotation_products OWNER TO postgres;

--
-- Name: quotation_products_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.quotation_products ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.quotation_products_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: quotation_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.quotation_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.quotation_seq OWNER TO postgres;

--
-- Name: salesorder; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.salesorder (
    id bigint NOT NULL,
    cessamount numeric(12,2),
    cgstamount numeric(12,2),
    creationdate timestamp(6) without time zone,
    currency character varying(20),
    deleteddate timestamp(6) without time zone,
    deliveryaddress character varying(500),
    deliverydate date,
    discountamount numeric(12,2),
    dispatchthrough character varying(100),
    ewaybillnumber character varying(50),
    freightandforwardingcharges numeric(12,2),
    igstamount numeric(12,2),
    incoterms character varying(200),
    netamount numeric(12,2),
    orderdate date NOT NULL,
    ordernumber character varying(255) NOT NULL,
    packaginginstructions character varying(200),
    paymentterms character varying(200),
    podate date,
    ponumber character varying(50),
    reference character varying(200),
    remarks character varying(500),
    roundoffamount numeric(12,2),
    sgstamount numeric(12,2),
    shippingmethod character varying(100),
    status character varying(255) NOT NULL,
    subtotal numeric(12,2),
    taxablevalue numeric(12,2),
    transportmode character varying(50),
    updateddate timestamp(6) without time zone,
    vouchertype character varying(255) NOT NULL,
    customer_id integer NOT NULL,
    quotation_id integer,
    discountpercentage numeric(12,2),
    includefreightcharges boolean NOT NULL,
    taxpercentage numeric(5,2),
    taxtype character varying(255),
    totalpayableamount numeric(12,2),
    CONSTRAINT salesorder_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'IN_PROGRESS'::character varying, 'DISPATCHED'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT salesorder_taxtype_check CHECK (((taxtype)::text = ANY ((ARRAY['IGST'::character varying, 'CGST_SGST'::character varying])::text[]))),
    CONSTRAINT salesorder_vouchertype_check CHECK (((vouchertype)::text = ANY ((ARRAY['SALES_ORDER'::character varying, 'DELIVERY_NOTE'::character varying, 'TAX_INVOICE'::character varying])::text[])))
);


ALTER TABLE public.salesorder OWNER TO postgres;

--
-- Name: salesorder_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.salesorder_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.salesorder_seq OWNER TO postgres;

--
-- Name: salesorderdispatchdetail; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.salesorderdispatchdetail (
    id bigint NOT NULL,
    deliveryreference character varying(255),
    dispatchdate timestamp(6) without time zone,
    dispatchedquantity double precision NOT NULL,
    inventory_instance_id bigint,
    sales_order_item_id bigint
);


ALTER TABLE public.salesorderdispatchdetail OWNER TO postgres;

--
-- Name: salesorderdispatchdetail_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.salesorderdispatchdetail_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.salesorderdispatchdetail_seq OWNER TO postgres;

--
-- Name: salesorderitem; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.salesorderitem (
    id bigint NOT NULL,
    cessamount numeric(12,2),
    cessrate numeric(12,2),
    cgstamount numeric(12,2),
    cgstrate numeric(5,2),
    discountpercentage numeric(5,2),
    hsncode character varying(20) NOT NULL,
    igstamount numeric(12,2),
    igstrate numeric(5,2),
    linetaxablevalue numeric(12,2),
    linetotal numeric(12,2),
    quantity numeric(10,2),
    sgstamount numeric(12,2),
    sgstrate numeric(5,2),
    unitprice numeric(12,2),
    inventoryItemId integer NOT NULL,
    sales_order_id bigint NOT NULL,
    itemrequestid bigint,
    priceperunit numeric(12,2),
    qty numeric(10,2),
    totalamountofproduct numeric(12,2),
    unitpriceafterdiscount numeric(12,2)
);


ALTER TABLE public.salesorderitem OWNER TO postgres;

--
-- Name: salesorderitem_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.salesorderitem_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.salesorderitem_seq OWNER TO postgres;

--
-- Name: taxinvoice; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.taxinvoice (
    id bigint NOT NULL,
    invoicedate timestamp(6) without time zone,
    invoiceno character varying(255),
    totalamount numeric(38,2),
    salesorder_id bigint
);


ALTER TABLE public.taxinvoice OWNER TO postgres;

--
-- Name: taxinvoice_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.taxinvoice_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.taxinvoice_seq OWNER TO postgres;

--
-- Name: workorderbomlist; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.workorderbomlist (
    id integer NOT NULL,
    expecteddeliverydate timestamp(6) without time zone,
    inventorystatus character varying(255),
    remarks character varying(255),
    bom_id integer,
    inventoryItemId integer,
    work_order_production_id integer,
    CONSTRAINT workorderbomlist_inventorystatus_check CHECK (((inventorystatus)::text = ANY ((ARRAY['AVAILABLE'::character varying, 'PENDING'::character varying, 'IN_PRODUCTION'::character varying, 'ORDERED'::character varying, 'RECEIVED'::character varying])::text[])))
);


ALTER TABLE public.workorderbomlist OWNER TO postgres;

--
-- Name: workorderbomlist_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.workorderbomlist_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.workorderbomlist_seq OWNER TO postgres;

--
-- Name: workorderjoblist; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.workorderjoblist (
    id integer NOT NULL,
    numberofhours numeric(10,1),
    production_job integer NOT NULL,
    workorderproductiontemplate_job_list_id integer NOT NULL
);


ALTER TABLE public.workorderjoblist OWNER TO postgres;

--
-- Name: workorderjoblist_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.workorderjoblist_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.workorderjoblist_seq OWNER TO postgres;

--
-- Name: workorderproduction; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.workorderproduction (
    id integer NOT NULL,
    actualcostofbom numeric(10,2),
    actualcostoflabour numeric(10,2),
    actualtotalcostofworkorder numeric(10,2),
    actualworkhours numeric(10,1),
    creationdate timestamp(6) without time zone,
    deleteddate timestamp(6) without time zone,
    duedate timestamp(6) without time zone,
    estimatedcostofbom numeric(10,2),
    estimatedcostoflabour numeric(10,2),
    iscreatechilditems boolean NOT NULL,
    overheadcostpercentage numeric(10,2),
    overheadcostvalue numeric(10,2),
    quantity double precision NOT NULL,
    remarks character varying(255),
    sourcetype character varying(255),
    startdate timestamp(6) without time zone,
    totalestimatedcostofworkorder numeric(10,2),
    updateddate timestamp(6) without time zone,
    workordernumber character varying(255) NOT NULL,
    workorderstatus character varying(255),
    parentworkorderproduction_id integer,
    salesorder_id bigint,
    workorderproductiontemplate_id integer NOT NULL,
    CONSTRAINT workorderproduction_sourcetype_check CHECK (((sourcetype)::text = ANY ((ARRAY['MANUAL'::character varying, 'SALES_ORDER'::character varying, 'CHILD_WORK_ORDER'::character varying])::text[]))),
    CONSTRAINT workorderproduction_workorderstatus_check CHECK (((workorderstatus)::text = ANY ((ARRAY['DRAFT'::character varying, 'IN_PROGRESS'::character varying, 'READY'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying])::text[])))
);


ALTER TABLE public.workorderproduction OWNER TO postgres;

--
-- Name: workorderproduction_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.workorderproduction_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.workorderproduction_seq OWNER TO postgres;

--
-- Name: workorderproductiontemplate; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.workorderproductiontemplate (
    id integer NOT NULL,
    creationdate timestamp(6) without time zone,
    deleteddate timestamp(6) without time zone,
    details character varying(255),
    estimatedcostofbom numeric(10,2),
    estimatedcostoflabour numeric(10,2),
    estimatedhours numeric(10,1),
    overheadcostpercentage numeric(10,2),
    overheadcostvalue numeric(10,2),
    totalcostofworkorder numeric(10,2),
    updateddate timestamp(6) without time zone,
    bom_id integer
);


ALTER TABLE public.workorderproductiontemplate OWNER TO postgres;

--
-- Name: workorderproductiontemplate_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.workorderproductiontemplate_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.workorderproductiontemplate_seq OWNER TO postgres;

--
-- Name: workorderproductiontemplatedocument; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.workorderproductiontemplatedocument (
    id bigint NOT NULL,
    filename character varying(255),
    filepath character varying(255),
    filetype character varying(255),
    uploadeddate timestamp(6) without time zone,
    workorderproductiontemplate_id integer NOT NULL
);


ALTER TABLE public.workorderproductiontemplatedocument OWNER TO postgres;

--
-- Name: workorderproductiontemplatedocument_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.workorderproductiontemplatedocument_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.workorderproductiontemplatedocument_seq OWNER TO postgres;

--
-- Name: bom bom_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.bom
    ADD CONSTRAINT bom_pkey PRIMARY KEY (id);


--
-- Name: bomattachment bomattachment_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.bomattachment
    ADD CONSTRAINT bomattachment_pkey PRIMARY KEY (id);


--
-- Name: bomposition bomposition_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.bomposition
    ADD CONSTRAINT bomposition_pkey PRIMARY KEY (id);


--
-- Name: contact_address contact_address_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.contact_address
    ADD CONSTRAINT contact_address_pkey PRIMARY KEY (id);


--
-- Name: contact_person_detail contact_person_detail_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.contact_person_detail
    ADD CONSTRAINT contact_person_detail_pkey PRIMARY KEY (id);


--
-- Name: contact contact_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.contact
    ADD CONSTRAINT contact_pkey PRIMARY KEY (id);


--
-- Name: deliverynote deliverynote_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.deliverynote
    ADD CONSTRAINT deliverynote_pkey PRIMARY KEY (id);


--
-- Name: deliverynoteitem deliverynoteitem_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.deliverynoteitem
    ADD CONSTRAINT deliverynoteitem_pkey PRIMARY KEY (id);


--
-- Name: employee_role employee_role_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.employee_role
    ADD CONSTRAINT employee_role_pkey PRIMARY KEY (id);


--
-- Name: employeedetails employeedetails_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.employeedetails
    ADD CONSTRAINT employeedetails_pkey PRIMARY KEY (id);


--
-- Name: enquiredproducts enquiredproducts_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.enquiredproducts
    ADD CONSTRAINT enquiredproducts_pkey PRIMARY KEY (id);


--
-- Name: enquiry enquiry_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.enquiry
    ADD CONSTRAINT enquiry_pkey PRIMARY KEY (id);


--
-- Name: enquiryconversationrecord enquiryconversationrecord_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.enquiryconversationrecord
    ADD CONSTRAINT enquiryconversationrecord_pkey PRIMARY KEY (id);



--
-- Name: inventorybookingapproval inventorybookingapproval_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inventorybookingapproval
    ADD CONSTRAINT inventorybookingapproval_pkey PRIMARY KEY (id);


--
-- Name: inventoryinstance inventoryinstance_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inventoryinstance
    ADD CONSTRAINT inventoryinstance_pkey PRIMARY KEY (id);


--
-- Name: inventoryitem inventoryitem_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inventoryitem
    ADD CONSTRAINT inventoryitem_pkey PRIMARY KEY (inventoryitemid);


--
-- Name: inventoryitemattachment inventoryitemattachment_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inventoryitemattachment
    ADD CONSTRAINT inventoryitemattachment_pkey PRIMARY KEY (id);


--
-- Name: inventoryledger inventoryledger_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inventoryledger
    ADD CONSTRAINT inventoryledger_pkey PRIMARY KEY (id);


--
-- Name: inventoryprocurementorder inventoryprocurementorder_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inventoryprocurementorder
    ADD CONSTRAINT inventoryprocurementorder_pkey PRIMARY KEY (id);


--
-- Name: inventoryrequest inventoryrequest_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inventoryrequest
    ADD CONSTRAINT inventoryrequest_pkey PRIMARY KEY (id);


--
-- Name: invoiceitem invoiceitem_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.invoiceitem
    ADD CONSTRAINT invoiceitem_pkey PRIMARY KEY (id);


--
-- Name: item_code item_code_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.item_code
    ADD CONSTRAINT item_code_pkey PRIMARY KEY (id);


--
-- Name: itemcodemapping itemcodemapping_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.itemcodemapping
    ADD CONSTRAINT itemcodemapping_pkey PRIMARY KEY (id);


--
-- Name: machinedetails machinedetails_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.machinedetails
    ADD CONSTRAINT machinedetails_pkey PRIMARY KEY (id);


--
-- Name: productionjob productionjob_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.productionjob
    ADD CONSTRAINT productionjob_pkey PRIMARY KEY (id);


--
-- Name: purchaseorder purchaseorder_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchaseorder
    ADD CONSTRAINT purchaseorder_pkey PRIMARY KEY (id);


--
-- Name: purchaseorderitem purchaseorderitem_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchaseorderitem
    ADD CONSTRAINT purchaseorderitem_pkey PRIMARY KEY (id);


--
-- Name: quotation quotation_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.quotation
    ADD CONSTRAINT quotation_pkey PRIMARY KEY (id);


--
-- Name: quotation_products quotation_products_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.quotation_products
    ADD CONSTRAINT quotation_products_pkey PRIMARY KEY (id);


--
-- Name: salesorder salesorder_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.salesorder
    ADD CONSTRAINT salesorder_pkey PRIMARY KEY (id);


--
-- Name: salesorderdispatchdetail salesorderdispatchdetail_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.salesorderdispatchdetail
    ADD CONSTRAINT salesorderdispatchdetail_pkey PRIMARY KEY (id);


--
-- Name: salesorderitem salesorderitem_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.salesorderitem
    ADD CONSTRAINT salesorderitem_pkey PRIMARY KEY (id);


--
-- Name: taxinvoice taxinvoice_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.taxinvoice
    ADD CONSTRAINT taxinvoice_pkey PRIMARY KEY (id);


--
-- Name: inventoryitem uk3n1enfva8ew0c7fewd1fdlpa0; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inventoryitem
    ADD CONSTRAINT uk3n1enfva8ew0c7fewd1fdlpa0 UNIQUE (itemcode);


--
-- Name: workorderproductiontemplate uk663wp925dw3bqstvubeuipmy3; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.workorderproductiontemplate
    ADD CONSTRAINT uk663wp925dw3bqstvubeuipmy3 UNIQUE (bom_id);


--
-- Name: quotation uka4c4dhvj8kppjsbf97qoyl1p3; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.quotation
    ADD CONSTRAINT uka4c4dhvj8kppjsbf97qoyl1p3 UNIQUE (qtnno);


--
-- Name: salesorder ukaewq5e92t33s6t75efu5w6i28; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.salesorder
    ADD CONSTRAINT ukaewq5e92t33s6t75efu5w6i28 UNIQUE (ordernumber);


--
-- Name: inventoryinstance ukb6kobstg43q2ipqwf2aiah4y5; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inventoryinstance
    ADD CONSTRAINT ukb6kobstg43q2ipqwf2aiah4y5 UNIQUE (uniqueid);


--
-- Name: enquiry ukcuvga82jqcqrw8yxsaqip3bt2; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.enquiry
    ADD CONSTRAINT ukcuvga82jqcqrw8yxsaqip3bt2 UNIQUE (enqno);


--
-- Name: item_code ukflxl6yym5un91sf382s6db9nv; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.item_code
    ADD CONSTRAINT ukflxl6yym5un91sf382s6db9nv UNIQUE (code);


--
-- Name: productionjob ukrjru2pwpq0ssi536vsd8qwgxa; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.productionjob
    ADD CONSTRAINT ukrjru2pwpq0ssi536vsd8qwgxa UNIQUE (jobname);


--
-- Name: workorderproduction ukt6ep2p4frd15qscq8xxdfcndk; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.workorderproduction
    ADD CONSTRAINT ukt6ep2p4frd15qscq8xxdfcndk UNIQUE (workordernumber);


--
-- Name: workorderbomlist workorderbomlist_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.workorderbomlist
    ADD CONSTRAINT workorderbomlist_pkey PRIMARY KEY (id);


--
-- Name: workorderjoblist workorderjoblist_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.workorderjoblist
    ADD CONSTRAINT workorderjoblist_pkey PRIMARY KEY (id);


--
-- Name: workorderproduction workorderproduction_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.workorderproduction
    ADD CONSTRAINT workorderproduction_pkey PRIMARY KEY (id);


--
-- Name: workorderproductiontemplate workorderproductiontemplate_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.workorderproductiontemplate
    ADD CONSTRAINT workorderproductiontemplate_pkey PRIMARY KEY (id);


--
-- Name: workorderproductiontemplatedocument workorderproductiontemplatedocument_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.workorderproductiontemplatedocument
    ADD CONSTRAINT workorderproductiontemplatedocument_pkey PRIMARY KEY (id);




--
-- Name: idx_invinst_booked_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_invinst_booked_date ON public.inventoryinstance USING btree (bookeddate);


--
-- Name: idx_invinst_deleted_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_invinst_deleted_date ON public.inventoryinstance USING btree (deleteddate);


--
-- Name: idx_invinst_filter_combo; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_invinst_filter_combo ON public.inventoryinstance USING btree (inventoryitemref, bookeddate, deleteddate);


--
-- Name: idx_invinst_is_consumed; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_invinst_is_consumed ON public.inventoryinstance USING btree (isconsumed);


--
-- Name: idx_invinst_itemref; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_invinst_itemref ON public.inventoryinstance USING btree (inventoryitemref);


--
-- Name: inventoryinstance fk10m5r0bc2fuy9cce7ksx30fev; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inventoryinstance
    ADD CONSTRAINT fk10m5r0bc2fuy9cce7ksx30fev FOREIGN KEY (inventoryitemref) REFERENCES public.inventoryitem(inventoryitemid);


--
-- Name: enquiryconversationrecord fk110mv5guxx9694yqw2y2a32m7; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.enquiryconversationrecord
    ADD CONSTRAINT fk110mv5guxx9694yqw2y2a32m7 FOREIGN KEY (enquiry_conversation_id) REFERENCES public.enquiry(id);


--
-- Name: quotation_products fk2c0hkugn47tg2fhll8qjlidor; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.quotation_products
    ADD CONSTRAINT fk2c0hkugn47tg2fhll8qjlidor FOREIGN KEY (quotation_id) REFERENCES public.quotation(id);


--
-- Name: deliverynote fk2q2cv6enhl64prl0583ug06a4; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.deliverynote
    ADD CONSTRAINT fk2q2cv6enhl64prl0583ug06a4 FOREIGN KEY (salesorder_id) REFERENCES public.salesorder(id);


--
-- Name: inventorybookingapproval fk2u9rs1ijmsao1k8gpj45jyweb; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inventorybookingapproval
    ADD CONSTRAINT fk2u9rs1ijmsao1k8gpj45jyweb FOREIGN KEY (instancerequestid) REFERENCES public.inventoryrequest(id);


--
-- Name: workorderjoblist fk46y1hkl5ijhxqdpbj4etealh9; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.workorderjoblist
    ADD CONSTRAINT fk46y1hkl5ijhxqdpbj4etealh9 FOREIGN KEY (production_job) REFERENCES public.productionjob(id);


--
-- Name: purchaseorder fk4c8740ni9dpo6defb8k2c5rt8; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchaseorder
    ADD CONSTRAINT fk4c8740ni9dpo6defb8k2c5rt8 FOREIGN KEY (salesorder_id) REFERENCES public.salesorder(id);


--
-- Name: inventoryitemattachment fk4pjdj3y8j9ud72ojign8m0biy; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inventoryitemattachment
    ADD CONSTRAINT fk4pjdj3y8j9ud72ojign8m0biy FOREIGN KEY (inventoryItemId) REFERENCES public.inventoryitem(inventoryitemid);


--
-- Name: workorderbomlist fk67ht2173dbh7uolokvik1eyuv; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.workorderbomlist
    ADD CONSTRAINT fk67ht2173dbh7uolokvik1eyuv FOREIGN KEY (bom_id) REFERENCES public.bom(id);


--
-- Name: workorderjoblist fk6yq4dh3urv0i38ub5mjolr05b; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.workorderjoblist
    ADD CONSTRAINT fk6yq4dh3urv0i38ub5mjolr05b FOREIGN KEY (workorderproductiontemplate_job_list_id) REFERENCES public.workorderproductiontemplate(id);


--
-- Name: salesorder fk7pmc2u1tujq99e8ij0kr6sccs; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.salesorder
    ADD CONSTRAINT fk7pmc2u1tujq99e8ij0kr6sccs FOREIGN KEY (quotation_id) REFERENCES public.quotation(id);


--
-- Name: quotation fk81tom0n2ta65sb8o326ih2ipn; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.quotation
    ADD CONSTRAINT fk81tom0n2ta65sb8o326ih2ipn FOREIGN KEY (enquiry_id) REFERENCES public.enquiry(id);


--
-- Name: inventoryinstance fk8glggecnoflotw3i1vjwpvm65; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inventoryinstance
    ADD CONSTRAINT fk8glggecnoflotw3i1vjwpvm65 FOREIGN KEY (purchase_order_item_id) REFERENCES public.purchaseorderitem(id);


--
-- Name: salesorderdispatchdetail fk8tsds924105j30o9g1joc0mxj; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.salesorderdispatchdetail
    ADD CONSTRAINT fk8tsds924105j30o9g1joc0mxj FOREIGN KEY (sales_order_item_id) REFERENCES public.salesorderitem(id);


--
-- Name: taxinvoice fk8ump98g3f01hm6pf5aalesb46; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.taxinvoice
    ADD CONSTRAINT fk8ump98g3f01hm6pf5aalesb46 FOREIGN KEY (salesorder_id) REFERENCES public.salesorder(id);


--
-- Name: bomposition fk93of9kyo1jtx37pdjcp1vtlmh; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.bomposition
    ADD CONSTRAINT fk93of9kyo1jtx37pdjcp1vtlmh FOREIGN KEY (bompositionid) REFERENCES public.bom(id);


--
-- Name: salesorder fk9hayh9jh6q459ycxvsek4fapu; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.salesorder
    ADD CONSTRAINT fk9hayh9jh6q459ycxvsek4fapu FOREIGN KEY (customer_id) REFERENCES public.contact(id);


--
-- Name: purchaseorderitem fk9uum874upy3e12jta4vb9n2el; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchaseorderitem
    ADD CONSTRAINT fk9uum874upy3e12jta4vb9n2el FOREIGN KEY (purchaseorder_id) REFERENCES public.purchaseorder(id);


--
-- Name: employee_role fkaot6bww542vg4ga2biuc7k7ij; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.employee_role
    ADD CONSTRAINT fkaot6bww542vg4ga2biuc7k7ij FOREIGN KEY (employee_id) REFERENCES public.employeedetails(id);


--
-- Name: purchaseorderitem fkbedegy11ww3hsg3ma20y36c96; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchaseorderitem
    ADD CONSTRAINT fkbedegy11ww3hsg3ma20y36c96 FOREIGN KEY (item_inventoryitemid) REFERENCES public.inventoryitem(inventoryitemid);


--
-- Name: inventoryinstance fkcqw8um5i0j542rr7o51a97qbg; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inventoryinstance
    ADD CONSTRAINT fkcqw8um5i0j542rr7o51a97qbg FOREIGN KEY (inventoryrequestid) REFERENCES public.inventoryrequest(id);


--
-- Name: inventoryinstance fkdnn5y9oxvhear1ehgw6ywbant; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inventoryinstance
    ADD CONSTRAINT fkdnn5y9oxvhear1ehgw6ywbant FOREIGN KEY (inventory_ledger_id) REFERENCES public.inventoryledger(id);


--
-- Name: deliverynoteitem fkdpoq3jc2moh9kk8tipq02p9fp; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.deliverynoteitem
    ADD CONSTRAINT fkdpoq3jc2moh9kk8tipq02p9fp FOREIGN KEY (inventoryitem_inventoryitemid) REFERENCES public.inventoryitem(inventoryitemid);


--
-- Name: inventoryprocurementorder fkeo4yxj0llfh3y96r1ks8wwknq; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inventoryprocurementorder
    ADD CONSTRAINT fkeo4yxj0llfh3y96r1ks8wwknq FOREIGN KEY (procurementrequestid) REFERENCES public.inventoryrequest(id);


--
-- Name: workorderproductiontemplate fkes44si15mcsok0lt4a8flsihk; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.workorderproductiontemplate
    ADD CONSTRAINT fkes44si15mcsok0lt4a8flsihk FOREIGN KEY (bom_id) REFERENCES public.bom(id);


--
-- Name: bomposition fkew6fvby7ekq8ppxm8hmepxn58; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.bomposition
    ADD CONSTRAINT fkew6fvby7ekq8ppxm8hmepxn58 FOREIGN KEY (inventoryitemid) REFERENCES public.inventoryitem(inventoryitemid);


--
-- Name: inventoryrequest fkfoeb3l6rnng9c3jwutmfaq6pq; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inventoryrequest
    ADD CONSTRAINT fkfoeb3l6rnng9c3jwutmfaq6pq FOREIGN KEY (inventoryitemrequest) REFERENCES public.inventoryitem(inventoryitemid);


--
-- Name: enquiry fkgrgev8cbosh0yk7ir2gq8lfub; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.enquiry
    ADD CONSTRAINT fkgrgev8cbosh0yk7ir2gq8lfub FOREIGN KEY (contact_id) REFERENCES public.contact(id);


--
-- Name: bomattachment fkhcmyukpjkpqgy1nk1pdmukuj4; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.bomattachment
    ADD CONSTRAINT fkhcmyukpjkpqgy1nk1pdmukuj4 FOREIGN KEY (bom_id) REFERENCES public.bom(id);


--
-- Name: bom fkhfk5evomv0y33u3n8uibkadn8; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.bom
    ADD CONSTRAINT fkhfk5evomv0y33u3n8uibkadn8 FOREIGN KEY (parentinventoryitemid) REFERENCES public.inventoryitem(inventoryitemid);


--
-- Name: workorderbomlist fkhwpc02kcxfgy9jdi9et1ois7b; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.workorderbomlist
    ADD CONSTRAINT fkhwpc02kcxfgy9jdi9et1ois7b FOREIGN KEY (inventoryItemId) REFERENCES public.inventoryitem(inventoryitemid);


--
-- Name: enquiredproducts fkhyd37vyrhej93h30ihvxpa2j6; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.enquiredproducts
    ADD CONSTRAINT fkhyd37vyrhej93h30ihvxpa2j6 FOREIGN KEY (inventoryItemId) REFERENCES public.inventoryitem(inventoryitemid);


--
-- Name: productionjob fkj8e4ko4sdi5trxvryaimnewpk; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.productionjob
    ADD CONSTRAINT fkj8e4ko4sdi5trxvryaimnewpk FOREIGN KEY (machine_details_id) REFERENCES public.machinedetails(id);


--
-- Name: quotation_products fkjtug5dm28ku7agexuji4wipxi; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.quotation_products
    ADD CONSTRAINT fkjtug5dm28ku7agexuji4wipxi FOREIGN KEY (inventoryItemId) REFERENCES public.inventoryitem(inventoryitemid);


--
-- Name: invoiceitem fkl3qdnbnwsap5xg5ofe0rubbsf; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.invoiceitem
    ADD CONSTRAINT fkl3qdnbnwsap5xg5ofe0rubbsf FOREIGN KEY (inventoryitem_inventoryitemid) REFERENCES public.inventoryitem(inventoryitemid);


--
-- Name: contact_person_detail fkm9mj51ov2w55ocgt0us9xum0j; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.contact_person_detail
    ADD CONSTRAINT fkm9mj51ov2w55ocgt0us9xum0j FOREIGN KEY (contact_id) REFERENCES public.contact(id);


--
-- Name: workorderproduction fkmei2l0g92u4vwaaipcshnh0g8; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.workorderproduction
    ADD CONSTRAINT fkmei2l0g92u4vwaaipcshnh0g8 FOREIGN KEY (parentworkorderproduction_id) REFERENCES public.workorderproduction(id);


--
-- Name: workorderproduction fkmoma35j1w68qpin6b2pr2clt; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.workorderproduction
    ADD CONSTRAINT fkmoma35j1w68qpin6b2pr2clt FOREIGN KEY (workorderproductiontemplate_id) REFERENCES public.workorderproductiontemplate(id);


--
-- Name: deliverynoteitem fkmsxyufp7g1f7fndv78dhxc5ww; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.deliverynoteitem
    ADD CONSTRAINT fkmsxyufp7g1f7fndv78dhxc5ww FOREIGN KEY (deliverynote_id) REFERENCES public.deliverynote(id);


--
-- Name: inventoryinstance fkn4wk3dd7faqs2dnbay9hc8ym3; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inventoryinstance
    ADD CONSTRAINT fkn4wk3dd7faqs2dnbay9hc8ym3 FOREIGN KEY (delivery_note_item_id) REFERENCES public.deliverynoteitem(id);


--
-- Name: inventoryinstance fkn6gfsoe5fsm73iluxqd3haje9; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inventoryinstance
    ADD CONSTRAINT fkn6gfsoe5fsm73iluxqd3haje9 FOREIGN KEY (procurementorderid) REFERENCES public.inventoryprocurementorder(id);


--
-- Name: purchaseorder fko0eiskgflnhih34f0kek1alt5; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.purchaseorder
    ADD CONSTRAINT fko0eiskgflnhih34f0kek1alt5 FOREIGN KEY (vendor_id) REFERENCES public.contact(id);


--
-- Name: salesorderitem fkoed7o98nt271c9r641fkn2hjk; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.salesorderitem
    ADD CONSTRAINT fkoed7o98nt271c9r641fkn2hjk FOREIGN KEY (inventoryItemId) REFERENCES public.inventoryitem(inventoryitemid);


--
-- Name: inventoryprocurementorder fkonreti7be3jwd0u4y6rcdf67u; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inventoryprocurementorder
    ADD CONSTRAINT fkonreti7be3jwd0u4y6rcdf67u FOREIGN KEY (inventoryitemprocurementrequest) REFERENCES public.inventoryitem(inventoryitemid);


--
-- Name: inventoryinstance fkonyjt1qjcwfxmffmbp2flksmv; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inventoryinstance
    ADD CONSTRAINT fkonyjt1qjcwfxmffmbp2flksmv FOREIGN KEY (sales_order_item_id) REFERENCES public.salesorderitem(id);


--
-- Name: workorderproduction fkpkadjjsqetb3iwln57x4ec8on; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.workorderproduction
    ADD CONSTRAINT fkpkadjjsqetb3iwln57x4ec8on FOREIGN KEY (salesorder_id) REFERENCES public.salesorder(id);


--
-- Name: salesorderitem fkqg72gn0spsaa01huni2n5wtdp; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.salesorderitem
    ADD CONSTRAINT fkqg72gn0spsaa01huni2n5wtdp FOREIGN KEY (sales_order_id) REFERENCES public.salesorder(id);


--
-- Name: contact_address fkqqxykpjj1qrgxle7cpp0txicc; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.contact_address
    ADD CONSTRAINT fkqqxykpjj1qrgxle7cpp0txicc FOREIGN KEY (contact_id) REFERENCES public.contact(id);


--
-- Name: workorderproductiontemplatedocument fkqyb53ud7w3onr62dk6yrb3rcb; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.workorderproductiontemplatedocument
    ADD CONSTRAINT fkqyb53ud7w3onr62dk6yrb3rcb FOREIGN KEY (workorderproductiontemplate_id) REFERENCES public.workorderproductiontemplate(id);


--
-- Name: inventoryledger fkrdfgs535nc81q43agf91cbrbs; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inventoryledger
    ADD CONSTRAINT fkrdfgs535nc81q43agf91cbrbs FOREIGN KEY (inventoryitem_inventoryitemid) REFERENCES public.inventoryitem(inventoryitemid);


--
-- Name: invoiceitem fks7l8i6onyy9xiowqa7ke2nrh5; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.invoiceitem
    ADD CONSTRAINT fks7l8i6onyy9xiowqa7ke2nrh5 FOREIGN KEY (taxinvoice_id) REFERENCES public.taxinvoice(id);


--
-- Name: workorderbomlist fkscjcgg0mlxlnvneknfs8squ79; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.workorderbomlist
    ADD CONSTRAINT fkscjcgg0mlxlnvneknfs8squ79 FOREIGN KEY (work_order_production_id) REFERENCES public.workorderproduction(id);


--
-- Name: salesorderdispatchdetail fksuk0govjo3g6q5glvyhm38gc0; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.salesorderdispatchdetail
    ADD CONSTRAINT fksuk0govjo3g6q5glvyhm38gc0 FOREIGN KEY (inventory_instance_id) REFERENCES public.inventoryinstance(id);


--
-- Name: inventoryinstance fkt8e4yokl04pjis756vlgr5dk8; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inventoryinstance
    ADD CONSTRAINT fkt8e4yokl04pjis756vlgr5dk8 FOREIGN KEY (work_order_id) REFERENCES public.workorderbomlist(id);


--
-- Name: enquiredproducts fkumw32jmho9n0qva8ga3cd55i; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.enquiredproducts
    ADD CONSTRAINT fkumw32jmho9n0qva8ga3cd55i FOREIGN KEY (enquiry_id) REFERENCES public.enquiry(id);


--
-- PostgreSQL database dump complete
--

