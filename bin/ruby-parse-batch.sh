
## Usage: bash bin/ruby-parse-batch.sh lib/ruby-jimw-code/rails/
rbpath=$1
# sudo gem install parser
# sudo gem install unparser

echo "Clear AST file..."
find $rbpath -name '*.ast' | xargs -I file rm file
for file in ` find $rbpath -name "*.rb" `; do echo "======"$file ; ruby-parse $file > $file.ast; done
# => lib/ruby-jimw-code/rails//activemodel/lib/active_model/validations/clusivity.rb.ast
