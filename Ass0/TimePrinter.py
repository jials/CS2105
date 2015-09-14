__author__ = 'jiale'
import time
import threading
import sys

output_str = start_time = interval = None

output_str = sys.argv[1]
start_time = sys.argv[2]
interval = sys.argv[3]

time.sleep(float(start_time))

def print_message():
    t = threading.Timer(float(interval), print_message)
    t.daemon = True
    t.start()
    print output_str
    for line in iter(lambda: raw_input(), "q"):
        t.stop()

print_message()