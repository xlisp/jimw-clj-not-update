#!/usr/bin/env python
import sys
import ast

# FROM: http://felipeochoa.github.io/aqua-lisp/2014/10/24/a-python-dsl/
# Usage: ./bin/py2lisp.py bin/py2lisp.py

def translate(node_or_literal):
    "Convert an AST node or Python literal into a Lisp form (recursively)."
    if isinstance(node_or_literal, ast.AST):
        symbol = "|py-%s|" % node_or_literal.__class__.__name__
        if not node_or_literal._fields:  # this is a leaf node
            return "(%s)" % symbol
        args = " ".join(translate(sub_nl)
                        for _, sub_nl in ast.iter_fields(node_or_literal))
        return "(%s %s)" % (symbol, args)
    return translate_literal(node_or_literal)


def translate_literal(literal):
    "Translate a Python literal into a Lisp literal."
    if isinstance(literal, str):
        return "\"" + literal.replace("\"", "\\\"") + "\""
    elif isinstance(literal, bytes):
        return "#(%s)" % " ".join(map(str, literal))
    elif literal is None:
        return "|None|"
    elif isinstance(literal, bool):
        return "t" if literal else "nil"
    elif isinstance(literal, list):
        return "(%s)" % " ".join(map(translate, literal))
    elif isinstance(literal, complex):
        return "#C(%r %r)" % (literal.real, literal.imag)
    else:  # Should be an integer or float
        return repr(literal)

# AST: print(ast.dump(ast.parse(open(sys.argv[1], 'r').read())))

print(translate(ast.parse(open(sys.argv[1], 'r').read())))

