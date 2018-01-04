#-*-coding:utf-8 -*-
import jieba.posseg as pseg
import json
print json.dumps([[(word, flag) for word, flag in pseg.cut(words)] for words in json.loads(open("/home/clojure/jimw-clj/todos.json", "r").read())])
