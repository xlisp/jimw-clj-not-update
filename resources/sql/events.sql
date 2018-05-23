CREATE TABLE events (
  id BIGSERIAL PRIMARY KEY,
  event_name TEXT NOT NULL,
  info TEXT,
  event_data TEXT,
  blog BIGSERIAL, -- REFERENCES blogs (id), -- 支持C-g搜索博客上的选中内容
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ALTER TABLE events ADD COLUMN blog BIGSERIAL; --- REFERENCES blogs (id);

CREATE SEQUENCE events_new_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER TABLE events_new_id_seq OWNER TO jim;

ALTER SEQUENCE events_new_id_seq OWNED BY events.id;

ALTER TABLE ONLY events ALTER COLUMN id SET DEFAULT nextval('events_new_id_seq'::regclass);
