-- TODO: 自动更新pcm.ip: 只要app一打开,就插入一条ip记录到db里面,然后可以websocket更新网页的ip地址
CREATE TABLE pcmip (
  id BIGSERIAL PRIMARY KEY,
  ipaddress TEXT NOT NULL DEFAULT '0.0.0.0',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
