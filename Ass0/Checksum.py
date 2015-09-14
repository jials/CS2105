__author__ = 'jiale'
import binascii
import sys


def crc32(filename):
    crc = open(filename, 'rb').read()
    crc = binascii.crc32(crc) & 0xffffffff
    return crc

print crc32(sys.argv[1])