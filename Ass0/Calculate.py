__author__ = 'jiale'
import sys


op = sys.argv[1:]

operators = ['*', '/', '+', '-', '**']
op_map = {
    '*': lambda x, y: x*y,
    '/': lambda x, y: x/y,
    '+': lambda x, y: x+y,
    '-': lambda x, y: x-y,
    '**': lambda x, y: x**y,
}

if len(op) != 3:
    print "Incorrect number of arguments"
elif not isinstance(int(op[0]), int) or not isinstance(int(op[2]), int) or op[1] not in operators:
    print "Invalid inputs"
else:
    try:
        opr = op[1]
        op1 = int(op[0])
        op2 = int(op[2])
        result = op_map[opr](op1, op2)
        print result
    except ZeroDivisionError:
        print "Division by zero"