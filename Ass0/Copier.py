__author__ = 'jiale'
import shutil
import sys


src = sys.argv[1]
dst = sys.argv[2]

try:
    shutil.copyfile(src, dst)
    print src + " successfully copied to " + dst
except IOError:
    print "File not found"