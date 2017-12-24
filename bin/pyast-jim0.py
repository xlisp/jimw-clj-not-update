import ast
expr = """
def add(arg1, arg2):
    return arg1 + arg2
"""
expr_ast = ast.parse(expr)
ast.dump(expr_ast)

# => "Module(body=[FunctionDef(name='add', args=arguments(args=[Name(id='arg1', ctx=Param()), Name(id='arg2', ctx=Param())], vararg=None, kwarg=None, defaults=[]), body=[Return(value=BinOp(left=Name(id='arg1', ctx=Load()), op=Add(), right=Name(id='arg2', ctx=Load())))], decorator_list=[])])"

