CREATE TABLE todos (
  id BIGSERIAL PRIMARY KEY,
  blog BIGSERIAL NOT NULL REFERENCES blogs (id),
  parid BIGSERIAL TEXT NOT NULL,
  content TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
);

CREATE SEQUENCE todos_new_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER TABLE todos_new_id_seq OWNER TO jim;

ALTER SEQUENCE todos_new_id_seq OWNED BY todos.id;

ALTER TABLE ONLY todos ALTER COLUMN id SET DEFAULT nextval('todos_new_id_seq'::regclass);
