-- 用每个S表达式的commit的历史来表达一个复杂算法的演算过程
CREATE TABLE s_exp_commit (
  id BIGSERIAL PRIMARY KEY,
  -- before & after for ydiff: 修改前后对比
  s_exp_info_before TEXT NOT NULL, -- 创建s_exp_commit记录, eval的时候产生一个id, 保存在这个s表达式身上(Emacs如何缓存信息到文件某个位置上?)
  eval_result_before TEXT,
  s_exp_info_after TEXT, -- 关闭s_exp_commit记录, done设置为true
  eval_result_after TEXT,
  -- 最先实现scheme的jim0,jim1,jim2...的演算
  commit_info TEXT, -- 本次修改commit,done设置为true的时候,修改
  author TEXT NOT NULL DEFAULT 'stevechan', -- 支持导入
  s_exp_file_name TEXT NOT NULL, -- s表达式的所在文件
  done BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
