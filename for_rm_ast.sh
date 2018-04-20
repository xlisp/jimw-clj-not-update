#!/bin/bash

erro=true

while $erro;
do
    erro_info=`./read_pro.sh | grep -qi Exception && echo "Exception"`
    if [ $erro_info == "Exception" ]; then
        erro=true
    else
        erro=false
    fi

    if $erro ; then
        echo "~/CljPro/jimw-clj/`./read_pro.sh | tail -n 2 | head -n 1 | awk -F' >>>>>>|"' '{print $2}'`" | xargs rm
    fi
done
