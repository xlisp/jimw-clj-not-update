CREATE TYPE SOURCE_TYPE AS ENUM (
  'BLOG', -- 默认是博客
  'BOOK_OCR', -- 来源于扫描仪或照相机的文本
  'PDF_OCR',
  'RECORDING', -- 来源于录音
  'ML_VIDEO', -- 来源于吴恩达视频
  'WEB_ARTICLE', -- 来源于微信文章,或者网络文章
  'SEMANTIC_SEARCH', -- 来源于导入的S表达式代码
  'REVERSE_ENGINEERING', -- 来源于逆向工程代码
   -- -- 英语的自我设计
  'ENG_BLOG', 
  'ENG_BOOK_OCR',
  'ENG_PDF_OCR',
  'ENG_RECORDING', 
  'ENG_ML_VIDEO', 
  'ENG_WEB_ARTICLE'
);

--  ALTER TYPE SOURCE_TYPE ADD VALUE 'PDF_OCR';
--  ALTER TYPE SOURCE_TYPE ADD VALUE 'ENG_PDF_OCR';

CREATE TABLE blogs (
  id BIGSERIAL PRIMARY KEY,
  project TEXT,
  name TEXT NOT NULL,
  content TEXT NOT NULL,
  wctags JSONB NOT NULL DEFAULT '{}';
  source_type SOURCE_TYPE NOT NULL DEFAULT 'BLOG',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ALTER TABLE blogs ADD COLUMN wctags JSONB NOT NULL DEFAULT '{}';
-- ALTER TABLE blogs ADD COLUMN project TEXT;
-- ALTER TABLE blogs ADD COLUMN source_type SOURCE_TYPE NOT NULL DEFAULT 'BLOG';

CREATE SEQUENCE blogs_new_id_seq
    START WITH 4859
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER TABLE blogs_new_id_seq OWNER TO jim;

ALTER SEQUENCE blogs_new_id_seq OWNED BY blogs.id;

ALTER TABLE ONLY blogs ALTER COLUMN id SET DEFAULT nextval('blogs_new_id_seq'::regclass);
