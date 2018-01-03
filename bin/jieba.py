#!/usr/bin/python
#-*-coding:utf-8 -*-
import jieba.posseg as pseg
import sys
import json
str_arrays = sys.argv
str_arrays.pop(0)
print json.dumps([[(word, flag) for word, flag in pseg.cut(words)] for words in str_arrays])
