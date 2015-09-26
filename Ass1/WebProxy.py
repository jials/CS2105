__author__ = 'jiale'

import sys
import socket
import threading
import re

BACKLOG = 40    # specifies the maximum number of queued connections
BUFFER_SIZE_RECV = 1024     # max 1024 bytes will be read at a time


def usage():
    print "usage: " + sys.argv[0] + " <incoming-proxy-port-number>"


def main():
    # Check if port is given in system argument
    if len(sys.argv[1:]) != 1:
        usage()
        sys.exit(2)

    # load censored words
    censored_words = load_censortxt()

    # Configure host and port
    host = ''   # empty string specifies that the socket is reachable by any address the machine happens to have
    port = int(sys.argv[1])

    sys.stdout.write("Proxy server running on localhost: " + sys.argv[1] + '\n')

    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.bind((host, port))
        s.listen(BACKLOG)

    except socket.error, (errno, msg):
        if s:
            s.close()
        sys.stdout.write("Error opening socket: " + msg + '\n')
        sys.exit(1)    # indicating error

    while 1:
        conn, addr = s.accept()
        # thread for proxy server
        thread = threading.Thread(target=proxy_threading, args=(conn, addr, censored_words))
        thread.start()
    
    thread.join()
    s.close()


def proxy_threading(conn, addr, censored_words):
    # to get the request
    req = conn.recv(BUFFER_SIZE_RECV)

    first_line = req.split('\n')[0]
    url = first_line.split(' ')[1]

    sys.stdout.write("Request " + first_line + '\n')   # standard output for tracking

    http_pos = url.find('://')
    if http_pos == -1:
        mod_url = url
    else:
        mod_url = url[(http_pos+3):]

    port_pos = mod_url.find(":")           # find the port pos

    # find backslash, end of url
    end_pos = mod_url.find("/")
    if end_pos == -1:
        end_pos = len(mod_url)

    if port_pos == -1 or end_pos < port_pos:
        port = 80
        web_addr = mod_url[:end_pos]
    else:
        port = int((mod_url[(port_pos + 1):])[:end_pos - port_pos - 1])     # remove the backslash
        web_addr = mod_url[:port_pos]

    try:
        # create a socket to connect to web server
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.connect((web_addr, port))
        s.send(req)

        while 1:
            # receive data from web server
            res = s.recv(BUFFER_SIZE_RECV)

            # if respond is not empty
            if len(res) > 0:
                res = censor(res, censored_words)
                conn.send(res)
            else:
                break
        s.close()
        conn.close()

    except socket.error, (errno, msg):
        sys.stdout.write("Cannot establish connecting to web server: " + msg + '\n')
        conn.send("<h1>502 Bad Gateway<h1>" + '\n')     # HTTP502 if could not connect
        if s:
            s.close()
        if conn:
            conn.close()
        sys.exit(1)


def load_censortxt():
    CENSORTXT = "censor.txt"
    censored_words = []
    try:
        with open(CENSORTXT, 'r') as f:
            for line in f:
                word = line.rstrip()
                censored_words.append(word)

    except IOError:
        print "censor.txt is not found. No text censorship used"

    return censored_words


def censor(res, censored_words):
    count = 0
    lines = res.split('\n')

    # if the res is text or html
    if "Content-Type: text/html" or "Content-Type: text/plain" in res:
        for line in lines:
            if line == '\r':
                break
            count += 1

        for i in range(count, len(lines)):
            line = lines[i]
            for censored_word in censored_words:
                insensitive_censored_word = re.compile(re.escape(censored_word), re.IGNORECASE)
                line = insensitive_censored_word.sub('---', line)  # replace censored word with ---
            lines[i] = line

    modified_res = '\n'.join(lines)
    return modified_res

if __name__ == '__main__':
    main()