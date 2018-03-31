-- 用每个S表达式的commit的历史来表达一个复杂算法的演算过程
CREATE TABLE s_exp_commit (
  id BIGSERIAL PRIMARY KEY,
  -- before & after for ydiff: 修改前后对比
  s_exp_info_before TEXT NOT NULL,
  s_exp_info_after TEXT NOT NULL,
  -- 最先实现scheme的jim0,jim1,jim2...的演算
  commit_info TEXT NOT NULL, -- 本次修改commit
  author TEXT NOT NULL DEFAULT 'stevechan', -- 支持导入
  s_exp_file_name TEXT NOT NULL, -- s表达式的所在文件
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()  
);
