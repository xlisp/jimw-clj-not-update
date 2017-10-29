CREATE TABLE blogs (
  id BIGSERIAL PRIMARY KEY,
  name TEXT NOT NULL,
  content TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);


CREATE SEQUENCE blogs_new_id_seq
    START WITH 4859
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER TABLE blogs_new_id_seq OWNER TO jim;

ALTER SEQUENCE blogs_new_id_seq OWNED BY blogs.id;

ALTER TABLE ONLY blogs ALTER COLUMN id SET DEFAULT nextval('blogs_new_id_seq'::regclass);
