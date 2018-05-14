-- S表达式向量
CREATE TABLE s_exp_vector (
  id BIGSERIAL PRIMARY KEY,
  blog BIGSERIAL NOT NULL REFERENCES blogs (id),
  content TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  project TEXT NOT NULL
);
