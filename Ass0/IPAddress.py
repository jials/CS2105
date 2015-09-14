__author__ = 'jiale'


def convert(binary):
    output = ""
    for x in range(0, 32, 8):
        # print binary[x:x+8]
        output = output + str(int(binary[x:x+8], 2)) + "."
    return output[:-1]

output = convert(raw_input())
print output