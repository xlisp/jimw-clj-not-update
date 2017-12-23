
## Usage: bash bin/ruby-parse-batch.sh lib/ruby-jimw-code/rails/
rbpath=$1
# sudo gem install parser
# sudo gem install unparser

echo "Clear AST file..."
find $rbpath -name '*.ast' | xargs -I file rm file
for file in ` find $rbpath -name "*.rb" `; do echo "======"$file ; ruby-parse $file > $file.ast; done

echo "AST file count: " ` find $rbpath -name '*.ast' | wc -l `
echo "Ruby file count: " ` find $rbpath -name '*.rb' | wc -l `
