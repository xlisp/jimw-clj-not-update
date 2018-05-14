CREATE TABLE special_form (
  id BIGSERIAL PRIMARY KEY,
  content TEXT NOT NULL,  
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(content)
);
