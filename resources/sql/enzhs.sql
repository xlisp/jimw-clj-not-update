CREATE TABLE enzhs (
  id BIGSERIAL PRIMARY KEY,
  en_name TEXT NOT NULL,
  zh_name TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  -- 一个英文可以有多个中文词直译
  UNIQUE(en_name, zh_name)
);

CREATE SEQUENCE enzhs_new_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE enzhs_new_id_seq OWNED BY enzhs.id;

ALTER TABLE ONLY enzhs ALTER COLUMN id SET DEFAULT nextval('enzhs_new_id_seq'::regclass);
