CREATE TABLE record_error (
  id BIGSERIAL PRIMARY KEY,
  info TEXT,
  mac TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()  
);
