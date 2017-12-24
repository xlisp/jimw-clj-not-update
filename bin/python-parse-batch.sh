
## Usage: bash bin/python-parse-batch.sh lib/python-jimw-code/tensorflow/
rbpath=$1

echo "Clear AST file..."
find $rbpath -name '*.ast' | xargs -I file rm file
for file in ` find $rbpath -name "*.py" `; do echo "======"$file ; ./bin/py2lisp.py $file > $file.ast; done

echo "AST file count: " ` find $rbpath -name '*.ast' | wc -l `
echo "Ruby file count: " ` find $rbpath -name '*.py' | wc -l `


