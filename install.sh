#!/usr/bin/zsh
echo "Update code..."
cd ~/jimw-clj && git pull origin master
echo "Restart jimw-clj..."
cd ~/jimw-clj && cp ~/app.js ./target/cljsbuild/public/js/app.js
cd ~/jimw-clj &&  pm2 stop jimw-clj
sleep 2
cd ~/jimw-clj && pm2 start processes.json

