-- Emacs 的cider的S表达式
CREATE TABLE s_exp_history (
  id BIGSERIAL PRIMARY KEY,
  in_put TEXT NOT NULL,
  out_put TEXT,
  buffer_name TEXT,
  language_mode TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
