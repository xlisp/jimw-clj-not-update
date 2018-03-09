#!/bin/bash
PGHOST=localhost PGPORT=5432 PGDATABASE=blackberry PGUSER=postgres PGPASSWORD=123456 PGUSER=postgres PGDATABASE=blackberry PGPASSWORD=123456 pg_recvlogical --no-loop --dbname blackberry --slot blackberry_streaming --start --file -
