__author__ = 'jiale'
import re


field_to_val = {}
for line in iter(lambda: raw_input(), ""):
    field, val = re.split(':', line)
    field_to_val.setdefault(field.lower(), val.strip())
    # print field_to_val

for line in iter(lambda: raw_input(), "quit"):
    try:
        print field_to_val[line.lower()]
    except KeyError:
        print 'Unknown field'