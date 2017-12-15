CREATE TYPE DOT_TYPE AS ENUM (
  'TABLE',
  'RELATION'
);

CREATE CAST ( VARCHAR AS DOT_TYPE )
WITH INOUT AS IMPLICIT;

CREATE TABLE sqldots (
  id BIGSERIAL PRIMARY KEY,
  name TEXT NOT NULL,
  content TEXT NOT NULL,
  dot_type DOT_TYPE,
  defmodel INT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE SEQUENCE sqldots_new_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER TABLE sqldots_new_id_seq OWNER TO jim;

ALTER SEQUENCE sqldots_new_id_seq OWNED BY sqldots.id;

ALTER TABLE ONLY sqldots ALTER COLUMN id SET DEFAULT nextval('sqldots_new_id_seq'::regclass);
