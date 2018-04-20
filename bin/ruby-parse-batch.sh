
## Usage: bash bin/ruby-parse-batch.sh lib/ruby-jimw-code/rails/
rbpath=$1

# $ gpg --keyserver hkp://keys.gnupg.net --recv-keys 409B6B1796C275462A1703113804BB82D39DC0E3
# $ curl -sSL https://get.rvm.io | bash -s stable
# $ source ~/.rvm/scripts/rvm
# $ rvm install 2.3.0
# $ rvm use 2.3.0 --default

# $ gem install parser
# $ gem install unparser

echo "Clear AST file..."
find $rbpath -name '*.ast' | xargs -I file rm file
for file in ` find $rbpath -name "*.rb" `; do echo "======"$file ; ruby-parse $file > $file.ast; done

echo "AST file count: " ` find $rbpath -name '*.ast' | wc -l `
echo "Ruby file count: " ` find $rbpath -name '*.rb' | wc -l `

## 只有几个测试文件才有这样的错误解析, TODOS: 变成AST之前,就要过滤掉 "\xBE"
ag "error: literal contains escape sequences incompatible with UTF-8" | awk -F":" '{print $1}' | xargs -I file rm file

## Don't know how to create ISeq from: clojure.lang.Symbol => 不知是什么错误, .e.g: lib/ruby-jimw-code/rails/actionpack/test/dispatch/request/session_test.rb.ast
## TODOS: 先删除所有的测试先, 后面恢复,不要卡住大脑流
find $rbpath -name  "*_test.rb.ast" | xargs -I file rm file

