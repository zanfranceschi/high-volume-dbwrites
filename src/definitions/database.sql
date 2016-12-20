CREATE ROLE root LOGIN PASSWORD 'root'
	SUPERUSER INHERIT CREATEDB CREATEROLE REPLICATION;

CREATE DATABASE experimental WITH OWNER = root;

\connect experimental

CREATE TABLE public.thing
(
  id character varying(100) NOT NULL,
  name character varying(200) NOT NULL,
  status character varying(20) NOT NULL,
  CONSTRAINT thing_pk PRIMARY KEY (id)
)
WITH (
  OIDS=FALSE
);
