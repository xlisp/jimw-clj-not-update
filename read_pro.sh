#!/bin/bash
echo "(prn 1111111111) (in-ns 'jimw-clj.db.ruby) (read-string-for-pro (fn [code-list file-name] (map first code-list)) \"parser\")  :repl/quit " | netcat localhost 7751

