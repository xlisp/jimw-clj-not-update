#!/bin/bash

erro=true

while $erro;
do
    erro_info=`./read_pro.sh | grep Exception`
    if [ ! $erro_info ]; then
        erro=false
    else
        erro=true
    fi
    if $erro ; then
        echo "/home/clojure/jimw-clj/`./for_rm_ast.sh | tail -n 2 | head -n 1 | awk -F' >>>>>>|"' '{print $2}'`" | xargs rm
    fi
done
