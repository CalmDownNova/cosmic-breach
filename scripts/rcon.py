"""A tiny RCON client for a local test server's console (multiplayer test runs).

    python scripts/rcon.py <password> <command...>
    RCON_PORT (default 25575) and RCON_HOST (default 127.0.0.1) pick the server.

The test server needs enable-rcon=true, rcon.password and rcon.port in server.properties, and server-ip=127.0.0.1 so
the console only listens on this machine. Prints the server's reply.
"""
import os
import socket
import struct
import sys

LOGIN = 3
COMMAND = 2


def _packet(request_id, kind, body):
    data = struct.pack("<ii", request_id, kind) + body.encode("utf-8") + bytes(2)
    return struct.pack("<i", len(data)) + data


def _read_exactly(sock, n):
    data = b""
    while len(data) < n:
        chunk = sock.recv(n - len(data))
        if not chunk:
            raise ConnectionError("the server closed the console connection")
        data += chunk
    return data


def _read(sock):
    size = struct.unpack("<i", _read_exactly(sock, 4))[0]
    data = _read_exactly(sock, size)
    request_id, kind = struct.unpack("<ii", data[:8])
    return request_id, kind, data[8:-2].decode("utf-8", "replace")


def run(password, command, host="127.0.0.1", port=25575):
    with socket.create_connection((host, port), timeout=10) as sock:
        sock.sendall(_packet(1, LOGIN, password))
        request_id, _, _ = _read(sock)
        if request_id == -1:
            raise PermissionError("wrong console password")
        sock.sendall(_packet(2, COMMAND, command))
        _, _, reply = _read(sock)
        return reply


if __name__ == "__main__":
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(2)
    reply = run(sys.argv[1], " ".join(sys.argv[2:]), os.environ.get("RCON_HOST", "127.0.0.1"),
                int(os.environ.get("RCON_PORT", "25575")))
    print(reply)
