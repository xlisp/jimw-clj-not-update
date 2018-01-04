#-*-coding:utf-8 -*-
## python2.7 bin/jieba_cut.py 我爱北京 我爱Clojure
import jieba.posseg as pseg
import sys
import json
str_arrays = sys.argv
str_arrays.pop(0)
print json.dumps([[(word, flag) for word, flag in pseg.cut(words)] for words in str_arrays])
